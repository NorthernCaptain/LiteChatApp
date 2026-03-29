package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PollRequestDto(
    val after: String = "0"
)
