package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "reactions",
    primaryKeys = ["messageId", "emoji", "userId"],
    indices = [Index("messageId")]
)
data class ReactionEntity(
    val messageId: String,
    val emoji: String,
    val userId: Long
)
