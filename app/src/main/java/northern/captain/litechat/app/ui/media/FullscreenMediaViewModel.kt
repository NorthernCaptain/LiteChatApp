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

data class MediaUiState(
    val attachmentId: String = "",
    val filename: String = "",
    val mimeType: String = "",
    val isLoading: Boolean = true,
    val originalUrl: String? = null,
    val thumbnailUrl: String? = null,
    val localFilePath: String? = null,
    val isVideo: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FullscreenMediaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val attachmentRepository: AttachmentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    private val _uiState = MutableStateFlow(MediaUiState(attachmentId = attachmentId))
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val baseUrl = ApiConfig.BASE_URL
                val originalUrl = "$baseUrl/litechat/api/v1/attachments/$attachmentId"
                val thumbnailUrl = "$baseUrl/litechat/api/v1/attachments/$attachmentId/thumbnail"

                val attachment = messageDao.getAttachmentById(attachmentId)
                val mimeType = attachment?.mimeType ?: ""
                val filename = attachment?.originalFilename ?: ""
                val isVideo = mimeType.startsWith("video/")

                _uiState.update {
                    it.copy(
                        filename = filename,
                        mimeType = mimeType,
                        originalUrl = originalUrl,
                        thumbnailUrl = if (attachment?.hasThumbnail == true) thumbnailUrl else null,
                        isVideo = isVideo
                    )
                }

                if (isVideo) {
                    // Download video to local cache, then play from file
                    val localFile = withContext(Dispatchers.IO) {
                        attachmentRepository.downloadOriginal(attachmentId, filename)
                    }
                    _uiState.update {
                        it.copy(localFilePath = localFile.absolutePath, isLoading = false)
                    }
                } else {
                    // Images are loaded via Coil (already cached)
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
