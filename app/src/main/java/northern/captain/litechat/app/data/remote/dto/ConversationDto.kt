package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConversationDto(
    val id: String,
    val type: String,
    val name: String? = null,
    val createdBy: Long? = null,
    val members: List<ConversationMemberDto>,
    val lastMessage: ConversationLastMessageDto? = null,
    val createdAt: String? = null,
    val updatedAt: String
)
