package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateConversationRequestDto(
    val type: String,
    val memberIds: List<Long>,
    val name: String? = null
)
