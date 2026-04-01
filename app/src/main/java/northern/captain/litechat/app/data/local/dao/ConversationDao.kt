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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(conversation: ConversationEntity): Long

    @Query("""
        UPDATE conversations SET
            type = :type,
            name = :name,
            updatedAt = :updatedAt,
            lastMessageId = :lastMessageId,
            lastMessageText = :lastMessageText,
            lastMessageSenderId = :lastMessageSenderId,
            lastMessageCreatedAt = :lastMessageCreatedAt
        WHERE id = :id
    """)
    suspend fun updateConversation(
        id: String, type: String, name: String?, updatedAt: String,
        lastMessageId: String?, lastMessageText: String?,
        lastMessageSenderId: Long?, lastMessageCreatedAt: String?
    )

    @Transaction
    suspend fun upsert(conversation: ConversationEntity) {
        val rowId = insertIgnore(conversation)
        if (rowId == -1L) {
            updateConversation(
                conversation.id, conversation.type, conversation.name, conversation.updatedAt,
                conversation.lastMessageId, conversation.lastMessageText,
                conversation.lastMessageSenderId, conversation.lastMessageCreatedAt
            )
        }
    }

    @Transaction
    suspend fun upsertAll(conversations: List<ConversationEntity>) {
        conversations.forEach { upsert(it) }
    }

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
