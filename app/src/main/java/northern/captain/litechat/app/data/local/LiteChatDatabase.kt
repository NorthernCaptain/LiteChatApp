package northern.captain.litechat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import northern.captain.litechat.app.data.local.dao.ConversationDao
import northern.captain.litechat.app.data.local.dao.MessageDao
import northern.captain.litechat.app.data.local.dao.UserDao
import northern.captain.litechat.app.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        ConversationMemberEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ReactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LiteChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
