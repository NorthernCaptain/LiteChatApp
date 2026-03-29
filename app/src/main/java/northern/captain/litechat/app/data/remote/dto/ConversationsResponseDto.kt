package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConversationsResponseDto(
    val conversations: List<ConversationDto>
)
