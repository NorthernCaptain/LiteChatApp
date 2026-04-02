package northern.captain.litechat.app.data.local.dao

import androidx.room.*
import northern.captain.litechat.app.data.local.entity.AttachmentEntity
import northern.captain.litechat.app.data.local.entity.MessageEntity
import northern.captain.litechat.app.data.local.entity.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("""
        SELECT DISTINCT m.* FROM messages m
        LEFT JOIN attachments a ON a.messageId = m.id
        LEFT JOIN reactions r ON r.messageId = m.id
        WHERE m.conversationId = :conversationId
        ORDER BY CAST(m.id AS INTEGER) DESC
        LIMIT :limit
    """)
    fun getLatestMessagesFlow(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("""
        SELECT DISTINCT m.* FROM messages m
        LEFT JOIN attachments a ON a.messageId = m.id
        LEFT JOIN reactions r ON r.messageId = m.id
        WHERE m.conversationId = :conversationId
        ORDER BY CAST(m.id AS INTEGER) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getMessagesWindowFlow(conversationId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY CAST(id AS INTEGER) ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReactions(reactions: List<ReactionEntity>)

    @Query("SELECT * FROM attachments WHERE id = :attachmentId")
    suspend fun getAttachmentById(attachmentId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getAttachments(messageId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE messageId IN (:messageIds)")
    suspend fun getAttachmentsForMessages(messageIds: List<String>): List<AttachmentEntity>

    @Query("SELECT * FROM reactions WHERE messageId IN (:messageIds)")
    suspend fun getReactionsForMessages(messageIds: List<String>): List<ReactionEntity>

    @Query("SELECT * FROM reactions WHERE messageId = :messageId")
    suspend fun getReactions(messageId: String): List<ReactionEntity>

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND emoji = :emoji AND userId = :userId")
    suspend fun deleteReaction(messageId: String, emoji: String, userId: Long)

    @Query("SELECT MIN(CAST(id AS INTEGER)) FROM messages WHERE conversationId = :conversationId")
    suspend fun getOldestMessageId(conversationId: String): Long?

    @Query("SELECT MAX(CAST(id AS INTEGER)) FROM messages WHERE conversationId = :conversationId")
    suspend fun getLatestMessageId(conversationId: String): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("UPDATE messages SET delivered = 1 WHERE conversationId = :conversationId AND CAST(id AS INTEGER) <= CAST(:upToMessageId AS INTEGER) AND delivered = 0")
    suspend fun markDelivered(conversationId: String, upToMessageId: String)

    @Query("UPDATE messages SET readAt = 1 WHERE conversationId = :conversationId AND CAST(id AS INTEGER) <= CAST(:upToMessageId AS INTEGER) AND readAt = 0")
    suspend fun markRead(conversationId: String, upToMessageId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM attachments")
    suspend fun deleteAllAttachments()

    @Query("DELETE FROM reactions")
    suspend fun deleteAllReactions()
}
