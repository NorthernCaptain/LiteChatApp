package northern.captain.litechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: Long,
    val name: String,
    val avatar: String? = null
)
