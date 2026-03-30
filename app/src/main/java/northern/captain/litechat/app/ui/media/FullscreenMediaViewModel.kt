package northern.captain.litechat.app.ui.media

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import northern.captain.litechat.app.config.ApiConfig
import northern.captain.litechat.app.data.local.dao.MessageDao
import northern.captain.litechat.app.data.repository.AttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MediaItem(
    val attachmentId: String,
    val filename: String,
    val mimeType: String,
    val size: Long = 0,
    val isVideo: Boolean,
    val originalUrl: String,
    val thumbnailUrl: String?,
    val localFilePath: String? = null,
    val isLoading: Boolean = true,
    val downloadProgress: Float = 0f,
    val error: String? = null
)

data class MediaUiState(
    val items: List<MediaItem> = emptyList(),
    val initialPage: Int = 0,
    val isLoaded: Boolean = false
)

@HiltViewModel
class FullscreenMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val attachmentRepository: AttachmentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    private val messageId: String? = savedStateHandle["messageId"]
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val baseUrl = ApiConfig.BASE_URL
                val attachments = if (messageId != null) {
                    messageDao.getAttachments(messageId)
                } else {
                    val att = messageDao.getAttachmentById(attachmentId)
                    if (att != null) listOf(att) else emptyList()
                }

                val items = attachments.map { att ->
                    val isVideo = att.mimeType.startsWith("video/")
                    MediaItem(
                        attachmentId = att.id,
                        filename = att.originalFilename,
                        mimeType = att.mimeType,
                        size = att.size,
                        isVideo = isVideo,
                        originalUrl = "$baseUrl/litechat/api/v1/attachments/${att.id}",
                        thumbnailUrl = if (att.hasThumbnail) "$baseUrl/litechat/api/v1/attachments/${att.id}/thumbnail" else null,
                        isLoading = isVideo
                    )
                }

                val initialPage = items.indexOfFirst { it.attachmentId == attachmentId }.coerceAtLeast(0)

                _uiState.update {
                    it.copy(items = items, initialPage = initialPage, isLoaded = true)
                }

                // Pre-download video for the initial page
                items.getOrNull(initialPage)?.let { item ->
                    if (item.isVideo) downloadVideo(item.attachmentId, item.filename)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        items = listOf(MediaItem(
                            attachmentId = attachmentId,
                            filename = "",
                            mimeType = "",
                            isVideo = false,
                            originalUrl = "",
                            thumbnailUrl = null,
                            isLoading = false,
                            error = e.message
                        )),
                        isLoaded = true
                    )
                }
            }
        }
    }

    fun onPageSelected(page: Int) {
        val item = _uiState.value.items.getOrNull(page) ?: return
        if (item.isVideo && item.localFilePath == null && item.isLoading) {
            downloadVideo(item.attachmentId, item.filename)
        }
    }

    private fun downloadVideo(attId: String, filename: String) {
        val item = _uiState.value.items.find { it.attachmentId == attId } ?: return
        viewModelScope.launch {
            try {
                val localFile = withContext(Dispatchers.IO) {
                    attachmentRepository.downloadOriginal(attId, filename, item.size) { progress ->
                        _uiState.update { state ->
                            state.copy(items = state.items.map { i ->
                                if (i.attachmentId == attId) i.copy(downloadProgress = progress)
                                else i
                            })
                        }
                    }
                }
                _uiState.update { state ->
                    state.copy(items = state.items.map { item ->
                        if (item.attachmentId == attId) item.copy(
                            localFilePath = localFile.absolutePath,
                            isLoading = false
                        ) else item
                    })
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(items = state.items.map { item ->
                        if (item.attachmentId == attId) item.copy(
                            error = e.message,
                            isLoading = false
                        ) else item
                    })
                }
            }
        }
    }
}
