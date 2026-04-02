package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: Long,
    val text: String? = null,
    val referenceMessageId: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val reactions: List<ReactionGroupDto> = emptyList(),
    val createdAt: String,
    val delivered: Boolean = false,
    val readAt: Boolean = false
)
