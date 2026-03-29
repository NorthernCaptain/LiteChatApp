package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MessagesResponseDto(
    val messages: List<MessageDto>
)
