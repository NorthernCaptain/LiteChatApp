package northern.captain.litechat.app.domain.model

data class User(
    val userId: Long,
    val name: String,
    val avatar: String? = null
)
