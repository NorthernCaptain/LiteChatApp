package northern.captain.litechat.app.domain.model

data class Attachment(
    val id: String,
    val originalFilename: String,
    val mimeType: String,
    val size: Long,
    val hasThumbnail: Boolean,
    val thumbnailLocalPath: String? = null,
    val originalLocalPath: String? = null
)
