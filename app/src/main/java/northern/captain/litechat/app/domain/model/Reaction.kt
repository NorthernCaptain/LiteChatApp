package northern.captain.litechat.app.domain.model

data class ReactionGroup(
    val emoji: String,
    val userIds: List<Long>,
    val count: Int = userIds.size
)
