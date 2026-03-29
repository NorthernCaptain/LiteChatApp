package northern.captain.litechat.app.di

import android.content.Context
import androidx.room.Room
import northern.captain.litechat.app.data.local.LiteChatDatabase
import northern.captain.litechat.app.data.local.dao.ConversationDao
import northern.captain.litechat.app.data.local.dao.MessageDao
import northern.captain.litechat.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiteChatDatabase {
        return Room.databaseBuilder(
            context,
            LiteChatDatabase::class.java,
            "litechat.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideUserDao(db: LiteChatDatabase): UserDao = db.userDao()

    @Provides
    fun provideConversationDao(db: LiteChatDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: LiteChatDatabase): MessageDao = db.messageDao()
}
