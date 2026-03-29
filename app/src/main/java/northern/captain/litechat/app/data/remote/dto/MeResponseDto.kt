package northern.captain.litechat.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MeResponseDto(
    val userId: Long,
    val email: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val chatAccess: Int? = null
)
