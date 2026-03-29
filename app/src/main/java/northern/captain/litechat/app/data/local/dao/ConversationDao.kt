package northern.captain.litechat.app.data.local.dao

import androidx.room.*
import northern.captain.litechat.app.data.local.entity.ConversationEntity
import northern.captain.litechat.app.data.local.entity.ConversationMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<ConversationMemberEntity>)

    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId")
    suspend fun getMembers(conversationId: String): List<ConversationMemberEntity>

    @Query("SELECT * FROM conversation_members")
    fun getAllMembers(): Flow<List<ConversationMemberEntity>>

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :conversationId")
    suspend fun incrementUnread(conversationId: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun resetUnread(conversationId: String)

    @Query("""
        UPDATE conversations SET
            lastMessageId = :messageId,
            lastMessageText = :text,
            lastMessageSenderId = :senderId,
            lastMessageCreatedAt = :createdAt,
            updatedAt = :createdAt
        WHERE id = :conversationId
    """)
    suspend fun updateLastMessage(
        conversationId: String,
        messageId: String,
        text: String?,
        senderId: Long,
        createdAt: String
    )

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("DELETE FROM conversation_members")
    suspend fun deleteAllMembers()
}
