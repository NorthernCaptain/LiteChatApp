package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    val id: String,
    val originalFilename: String,
    val mimeType: String,
    val size: Long,
    val hasThumbnail: Boolean
)
