package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SendMessageRequestDto(
    val text: String? = null,
    val referenceMessageId: String? = null,
    val attachmentIds: List<Int>? = null
)
