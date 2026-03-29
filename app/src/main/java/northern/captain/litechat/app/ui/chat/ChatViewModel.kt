package northern.captain.litechat.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import northern.captain.litechat.app.data.remote.AuthManager
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
import kotlinx.coroutines.launch
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
    val messages: List<Message> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMoreMessages: Boolean = true,
    val inputText: String = "",
    val replyToMessage: Message? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val isSending: Boolean = false,
    val currentUserId: Long = 0,
    val isInitialLoading: Boolean = true
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val conversationId: String = savedStateHandle["conversationId"] ?: ""
    private val _uiState = MutableStateFlow(ChatUiState(currentUserId = authManager.getUserId()))

    // Sliding window: offset from the newest message (DESC order)
    // offset=0 means showing the latest PAGE_SIZE messages
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
                val name = if (convo.type == "direct") {
                    val otherUserId = convo.members.firstOrNull { it.userId != authManager.getUserId() }?.userId
                    otherUserId?.let { userRepository.getUserName(it) } ?: convo.name ?: "Chat"
                } else {
                    convo.name ?: "Group"
                }
                _uiState.update { it.copy(conversationName = name, conversationType = convo.type) }
            }
        }

        // Sync messages from API into Room
        viewModelScope.launch {
            try {
                messageRepository.syncAllMessages(conversationId)
            } catch (_: Exception) {}
            _uiState.update { it.copy(isInitialLoading = false) }
        }

        // Observe sliding window of messages from Room
        viewModelScope.launch {
            windowOffset
                .flatMapLatest { offset ->
                    messageRepository.getMessagesWindowFlow(conversationId, WINDOW_SIZE, offset)
                        .map { messages -> offset to messages }
                }
                .collect { (offset, messages) ->
                    if (messages.isEmpty() && offset > 0) {
                        // Scrolled past all messages — clamp back
                        windowOffset.value = (offset - WINDOW_SIZE).coerceAtLeast(0)
                    } else {
                        _uiState.update { it.copy(messages = messages) }
                    }
                }
        }
    }

    private var lastWindowShift = 0L

    /** Called when user scrolls up near the oldest visible messages */
    fun loadOlderMessages() {
        val now = System.currentTimeMillis()
        if (now - lastWindowShift < 500) return // debounce
        lastWindowShift = now
        windowOffset.value += WINDOW_SIZE
    }

    /** Called when user scrolls down near the newest visible messages */
    fun loadNewerMessages() {
        val now = System.currentTimeMillis()
        if (now - lastWindowShift < 500) return
        lastWindowShift = now
        val newOffset = (windowOffset.value - WINDOW_SIZE).coerceAtLeast(0)
        if (newOffset != windowOffset.value) {
            windowOffset.value = newOffset
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSendClick() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val attachmentIds = state.pendingAttachments.mapNotNull { it.attachmentId }

        if (text.isEmpty() && attachmentIds.isEmpty()) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                messageRepository.sendMessage(
                    conversationId = conversationId,
                    text = text.ifEmpty { null },
                    referenceMessageId = state.replyToMessage?.id,
                    attachmentIds = attachmentIds.ifEmpty { null }
                )
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
                _uiState.update { it.copy(isSending = false) }
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
                // Remove failed upload
                _uiState.update { state ->
                    state.copy(pendingAttachments = state.pendingAttachments.filter { it.localId != localId })
                }
            }
        }
    }

    fun onRemovePendingAttachment(localId: String) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filter { a -> a.localId != localId })
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
        pollManager.setActiveConversation(null)
    }
}
