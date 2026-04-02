package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("conversationId", "id")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: Long,
    val text: String? = null,
    val referenceMessageId: String? = null,
    val createdAt: String,
    val delivered: Boolean = false,
    val readAt: Boolean = false
)
