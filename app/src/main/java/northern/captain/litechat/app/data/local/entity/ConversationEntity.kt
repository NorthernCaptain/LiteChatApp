package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String? = null,
    val updatedAt: String,
    val lastMessageId: String? = null,
    val lastMessageText: String? = null,
    val lastMessageSenderId: Long? = null,
    val lastMessageCreatedAt: String? = null,
    val unreadCount: Int = 0
)
