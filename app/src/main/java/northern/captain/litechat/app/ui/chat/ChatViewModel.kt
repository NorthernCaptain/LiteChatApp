package northern.captain.litechat.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.repository.AttachmentRepository
import northern.captain.litechat.app.data.repository.ConversationRepository
import northern.captain.litechat.app.data.repository.MessageRepository
import northern.captain.litechat.app.data.repository.UserRepository
import northern.captain.litechat.app.domain.model.Attachment
import northern.captain.litechat.app.domain.model.Message
import northern.captain.litechat.app.domain.polling.PollManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import northern.captain.litechat.app.data.remote.dto.AckRequestDto
import northern.captain.litechat.app.data.remote.dto.TypingRequestDto
import java.io.File
import javax.inject.Inject

data class PendingAttachment(
    val localId: String,           // unique local ID assigned immediately
    val filename: String,
    val uri: Uri,
    val mimeType: String,
    val isUploading: Boolean = true,
    val attachmentId: Int? = null,  // set after upload
    val hasThumbnail: Boolean = false,
    val thumbnailUrl: String? = null // set after upload if server generated thumbnail
)

data class ChatUiState(
    val conversationName: String = "",
    val conversationType: String = "direct",
    val avatarUserId: Long? = null,
    val avatarFilename: String? = null,
    val messages: List<Message> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val inputText: String = "",
    val replyToMessage: Message? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isSending: Boolean = false,
    val currentUserId: Long = 0,
    val isInitialLoading: Boolean = true,
    val downloadingAttachmentId: String? = null,
    val uploadError: String? = null,
    val sendError: Boolean = false,
    val typingUsers: Map<Long, String> = emptyMap()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val pollManager: PollManager,
    private val authManager: AuthManager,
    private val api: LiteChatApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""
    private val _uiState = MutableStateFlow(ChatUiState(currentUserId = authManager.getUserId()))

    // Sliding window: offset from the newest message (DESC order)
    // offset=0 means showing the latest WINDOW_SIZE messages
    private val windowOffset = MutableStateFlow(0)

    companion object {
        const val WINDOW_SIZE = 50
    }

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _scrollToBottom = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottom: SharedFlow<Unit> = _scrollToBottom

    init {
        pollManager.setActiveConversation(conversationId)

        viewModelScope.launch {
            conversationRepository.resetUnread(conversationId)
        }

        // Load conversation name
        viewModelScope.launch {
            val convo = conversationRepository.getConversation(conversationId)
            if (convo != null) {
                val otherUserId = convo.members.firstOrNull { it.userId != authManager.getUserId() }?.userId
                val otherUser = otherUserId?.let { userRepository.getUser(it) }
                val name = if (convo.type == "direct") {
                    otherUser?.name ?: convo.name ?: "Chat"
                } else {
                    convo.name ?: "Group"
                }
                _uiState.update {
                    it.copy(
                        conversationName = name,
                        conversationType = convo.type,
                        avatarUserId = otherUserId,
                        avatarFilename = otherUser?.avatar
                    )
                }
            }
        }

        // Sync messages from API into Room, then send read receipt
        viewModelScope.launch {
            try {
                messageRepository.syncAllMessages(conversationId)
            } catch (_: Exception) {}
            _uiState.update { it.copy(isInitialLoading = false) }
            sendReadReceipt()
        }

        var lastReadReceiptId: String? = null

        // Observe sliding window of messages from Room
        viewModelScope.launch {
            windowOffset
                .flatMapLatest { offset ->
                    messageRepository.getMessagesWindowFlow(conversationId, WINDOW_SIZE, offset)
                        .map { messages -> offset to messages }
                }
                .collect { (offset, messages) ->
                    if (messages.isEmpty() && offset > 0) {
                        // Overshot past all messages — get the actual count and clamp
                        val total = messageRepository.getMessageCount(conversationId)
                        val maxOffset = (total - WINDOW_SIZE).coerceAtLeast(0)
                        windowOffset.value = maxOffset
                    } else {
                        _uiState.update { it.copy(messages = messages) }
                        // Send read receipt only when latest message changes
                        val latestId = messages.lastOrNull()?.id
                        if (offset == 0 && latestId != null && latestId != lastReadReceiptId) {
                            lastReadReceiptId = latestId
                            sendReadReceipt()
                        }
                    }
                }
        }

        // Collect typing events for this conversation
        viewModelScope.launch {
            pollManager.typingEvent.collect { event ->
                if (event.conversationId == conversationId) {
                    if (event.active) {
                        _uiState.update { it.copy(typingUsers = it.typingUsers + (event.userId to event.userName)) }
                        scheduleTypingRemoval(event.userId)
                    } else {
                        _uiState.update { it.copy(typingUsers = it.typingUsers - event.userId) }
                    }
                }
            }
        }
    }

    // Typing indicator management
    private var lastTypingSent = 0L
    private var typingStopJob: Job? = null
    private val typingRemovalJobs = mutableMapOf<Long, Job>()

    private fun scheduleTypingRemoval(userId: Long) {
        typingRemovalJobs[userId]?.cancel()
        typingRemovalJobs[userId] = viewModelScope.launch {
            kotlinx.coroutines.delay(6000)
            _uiState.update { it.copy(typingUsers = it.typingUsers - userId) }
        }
    }

    private fun sendTypingEvent(active: Boolean) {
        viewModelScope.launch {
            try {
                api.sendTypingEvent(conversationId, TypingRequestDto(active))
            } catch (_: Exception) {}
        }
    }

    private fun sendReadReceipt() {
        viewModelScope.launch {
            try {
                val latestId = messageRepository.getLatestCachedMessageId(conversationId) ?: return@launch
                api.acknowledgeRead(conversationId, AckRequestDto(latestId))
            } catch (_: Exception) {}
        }
    }

    private var lastWindowShift = 0L
    private val SHIFT_SIZE = 30 // shift by 30, keeping 20 message overlap

    /** Called when user scrolls up near the oldest visible messages */
    fun loadOlderMessages() {
        val now = System.currentTimeMillis()
        if (now - lastWindowShift < 500) return // debounce
        lastWindowShift = now
        windowOffset.value += SHIFT_SIZE
    }

    /** Called when user scrolls down near the newest visible messages */
    fun loadNewerMessages() {
        val now = System.currentTimeMillis()
        if (now - lastWindowShift < 500) return
        lastWindowShift = now
        val newOffset = (windowOffset.value - SHIFT_SIZE).coerceAtLeast(0)
        if (newOffset != windowOffset.value) {
            windowOffset.value = newOffset
        }
    }

    fun jumpToLatest() {
        windowOffset.value = 0
        _scrollToBottom.tryEmit(Unit)
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }

        // Send typing event (throttled to once per 5s)
        if (text.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastTypingSent >= 5000) {
                lastTypingSent = now
                sendTypingEvent(true)
            }
            // Reset stop-typing timer
            typingStopJob?.cancel()
            typingStopJob = viewModelScope.launch {
                kotlinx.coroutines.delay(5000)
                sendTypingEvent(false)
                lastTypingSent = 0
            }
        } else {
            // Input cleared
            typingStopJob?.cancel()
            if (lastTypingSent > 0) {
                sendTypingEvent(false)
                lastTypingSent = 0
            }
        }
    }

    fun onSendClick() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val attachmentIds = state.pendingAttachments.mapNotNull { it.attachmentId }

        if (text.isEmpty() && attachmentIds.isEmpty()) return

        // Stop typing indicator
        typingStopJob?.cancel()
        if (lastTypingSent > 0) {
            sendTypingEvent(false)
            lastTypingSent = 0
        }

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                withTimeout(5000) {
                    messageRepository.sendMessage(
                        conversationId = conversationId,
                        text = text.ifEmpty { null },
                        referenceMessageId = state.replyToMessage?.id,
                        attachmentIds = attachmentIds.ifEmpty { null }
                    )
                }
                _uiState.update {
                    it.copy(
                        inputText = "",
                        replyToMessage = null,
                        pendingAttachments = emptyList(),
                        isSending = false
                    )
                }
                _scrollToBottom.tryEmit(Unit)
            } catch (_: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = true) }
            }
        }
    }

    fun onAttachmentPicked(uri: Uri) {
        val localId = java.util.UUID.randomUUID().toString()
        val (filename, mimeType) = getFileInfo(uri)

        // Show placeholder immediately
        val placeholder = PendingAttachment(
            localId = localId,
            filename = filename,
            uri = uri,
            mimeType = mimeType,
            isUploading = true
        )
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments + placeholder)
        }

        // Upload in background, then update the entry
        viewModelScope.launch {
            try {
                val tempFile = copyUriToTempFile(uri, filename)
                val attachment = attachmentRepository.uploadAttachment(tempFile, mimeType)
                tempFile.delete()

                val thumbnailUrl = if (attachment.hasThumbnail) {
                    "${northern.captain.litechat.app.config.ApiConfig.BASE_URL}/litechat/api/v1/attachments/${attachment.id}/thumbnail"
                } else null

                _uiState.update { state ->
                    state.copy(pendingAttachments = state.pendingAttachments.map { p ->
                        if (p.localId == localId) p.copy(
                            isUploading = false,
                            attachmentId = attachment.id.toIntOrNull(),
                            hasThumbnail = attachment.hasThumbnail,
                            thumbnailUrl = thumbnailUrl
                        ) else p
                    })
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(
                        pendingAttachments = state.pendingAttachments.filter { it.localId != localId },
                        uploadError = filename
                    )
                }
            }
        }
    }

    fun onAttachmentsPicked(uris: List<Uri>) {
        uris.forEach { onAttachmentPicked(it) }
    }

    fun onCameraFileCaptured(file: File, mimeType: String) {
        val localId = java.util.UUID.randomUUID().toString()
        val placeholder = PendingAttachment(
            localId = localId,
            filename = file.name,
            uri = Uri.fromFile(file),
            mimeType = mimeType,
            isUploading = true
        )
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments + placeholder)
        }
        viewModelScope.launch {
            try {
                val attachment = attachmentRepository.uploadAttachment(file, mimeType)
                file.delete()
                val thumbnailUrl = if (attachment.hasThumbnail) {
                    "${northern.captain.litechat.app.config.ApiConfig.BASE_URL}/litechat/api/v1/attachments/${attachment.id}/thumbnail"
                } else null
                _uiState.update { state ->
                    state.copy(pendingAttachments = state.pendingAttachments.map { p ->
                        if (p.localId == localId) p.copy(
                            isUploading = false,
                            attachmentId = attachment.id.toIntOrNull(),
                            hasThumbnail = attachment.hasThumbnail,
                            thumbnailUrl = thumbnailUrl
                        ) else p
                    })
                }
            } catch (_: Exception) {
                file.delete()
                _uiState.update { state ->
                    state.copy(
                        pendingAttachments = state.pendingAttachments.filter { it.localId != localId },
                        uploadError = file.name
                    )
                }
            }
        }
    }

    fun clearSendError() {
        _uiState.update { it.copy(sendError = false) }
    }

    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }

    fun onRemovePendingAttachment(localId: String) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filter { a -> a.localId != localId })
        }
    }

    fun onOpenFile(attachmentId: String, filename: String, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingAttachmentId = attachmentId) }
            try {
                val file = attachmentRepository.downloadOriginal(attachmentId, filename)
                _uiState.update { it.copy(downloadingAttachmentId = null) }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(viewIntent, filename).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                _uiState.update { it.copy(downloadingAttachmentId = null) }
            }
        }
    }

    fun onReplyTo(message: Message) {
        _uiState.update { it.copy(replyToMessage = message) }
    }

    fun onCancelReply() {
        _uiState.update { it.copy(replyToMessage = null) }
    }

    fun onToggleReaction(messageId: String, emoji: String) {
        val userId = authManager.getUserId()
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val existing = message.reactions.find { it.emoji == emoji }
        val hasReacted = existing?.userIds?.contains(userId) == true

        viewModelScope.launch {
            try {
                if (hasReacted) {
                    messageRepository.removeReaction(messageId, emoji, userId)
                } else {
                    messageRepository.addReaction(messageId, emoji, userId)
                }
            } catch (_: Exception) {}
        }
    }

    private fun getFileInfo(uri: Uri): Pair<String, String> {
        var name = "file"
        var mimeType = "application/octet-stream"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: "file"
            }
        }
        context.contentResolver.getType(uri)?.let { mimeType = it }
        return name to mimeType
    }

    private fun copyUriToTempFile(uri: Uri, filename: String): File {
        val tempFile = File(context.cacheDir, filename)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    override fun onCleared() {
        super.onCleared()
        typingStopJob?.cancel()
        if (lastTypingSent > 0) {
            sendTypingEvent(false)
        }
        pollManager.setActiveConversation(null)
    }
}
