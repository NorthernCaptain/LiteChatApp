package northern.captain.litechat.app.domain.model

data class Conversation(
    val id: String,
    val type: String,
    val name: String?,
    val members: List<ConversationMember>,
    val lastMessage: LastMessage?,
    val updatedAt: String,
    val unreadCount: Int = 0
)

data class ConversationMember(
    val userId: Long,
    val joinedAt: String
)

data class LastMessage(
    val id: String,
    val senderId: Long,
    val text: String?,
    val createdAt: String
)
