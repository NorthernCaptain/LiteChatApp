package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "conversation_members",
    primaryKeys = ["conversationId", "userId"]
)
data class ConversationMemberEntity(
    val conversationId: String,
    val userId: Long,
    val joinedAt: String
)
