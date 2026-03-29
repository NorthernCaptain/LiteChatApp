package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReactionResponseDto(
    val id: String,
    val messageId: String,
    val userId: Long,
    val emoji: String,
    val createdAt: String
)
