package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PollEventDto(
    val pendingId: String,
    val type: String,
    val conversationId: String,
    val message: MessageDto? = null,
    val reaction: ReactionResponseDto? = null
)
