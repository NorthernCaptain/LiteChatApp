package northern.captain.litechat.app.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: Long,
    val senderName: String = "",
    val senderAvatar: String? = null,
    val text: String? = null,
    val referenceMessageId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val reactions: List<ReactionGroup> = emptyList(),
    val createdAt: String
)
