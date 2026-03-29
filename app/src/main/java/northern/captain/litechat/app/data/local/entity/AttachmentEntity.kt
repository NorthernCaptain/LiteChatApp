package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [Index("messageId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val originalFilename: String,
    val mimeType: String,
    val size: Long,
    val hasThumbnail: Boolean,
    val thumbnailLocalPath: String? = null,
    val originalLocalPath: String? = null
)
