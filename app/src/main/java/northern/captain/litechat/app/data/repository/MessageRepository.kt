package northern.captain.litechat.app.data.repository

import northern.captain.litechat.app.data.local.dao.MessageDao
import northern.captain.litechat.app.data.local.dao.UserDao
import northern.captain.litechat.app.data.local.entity.AttachmentEntity
import northern.captain.litechat.app.data.local.entity.MessageEntity
import northern.captain.litechat.app.data.local.entity.ReactionEntity
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.remote.dto.MessageDto
import northern.captain.litechat.app.data.remote.dto.ReactionResponseDto
import northern.captain.litechat.app.data.remote.dto.SendMessageRequestDto
import northern.captain.litechat.app.domain.model.Attachment
import northern.captain.litechat.app.domain.model.Message
import northern.captain.litechat.app.domain.model.ReactionGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val api: LiteChatApi,
    private val messageDao: MessageDao,
    private val userDao: UserDao
) {
    fun getMessagesWindowFlow(conversationId: String, limit: Int, offset: Int): Flow<List<Message>> {
        return messageDao.getMessagesWindowFlow(conversationId, limit, offset).map { entities ->
            if (entities.isEmpty()) return@map emptyList()
            val sorted = entities.sortedBy { it.id.toLongOrNull() ?: 0 }
            mapEntitiesToMessages(sorted)
        }
    }

    private suspend fun mapEntitiesToMessages(sorted: List<MessageEntity>): List<Message> {
        val messageIds = sorted.map { it.id }
        val attachments = messageDao.getAttachmentsForMessages(messageIds)
        val reactions = messageDao.getReactionsForMessages(messageIds)
        val attachmentsByMsg = attachments.groupBy { it.messageId }
        val reactionsByMsg = reactions.groupBy { it.messageId }

        // Build a lookup for referenced messages
        val refIds = sorted.mapNotNull { it.referenceMessageId }.distinct()
        val entityMap = sorted.associateBy { it.id }
        val refMessages = refIds.mapNotNull { refId ->
            entityMap[refId] ?: messageDao.getById(refId)
        }.associateBy { it.id }

        // Batch user lookups
        val allUserIds = (sorted.map { it.senderId } + refMessages.values.map { it.senderId }).distinct()
        val usersMap = allUserIds.mapNotNull { userDao.getById(it) }.associateBy { it.userId }

        // Look up attachments for referenced messages (for thumbnail in reply preview)
        val refMsgIds = refMessages.keys.toList()
        val refAttachments = if (refMsgIds.isNotEmpty()) {
            messageDao.getAttachmentsForMessages(refMsgIds).groupBy { it.messageId }
        } else emptyMap()

        return sorted.map { entity ->
            val sender = usersMap[entity.senderId]
            val refMsg = entity.referenceMessageId?.let { refMessages[it] }
            val refSender = refMsg?.let { usersMap[it.senderId] }
            val refAtts = refMsg?.let { refAttachments[it.id] } ?: emptyList()
            val refMediaAtt = refAtts.firstOrNull { it.hasThumbnail }
            val refThumbnailUrl = refMediaAtt?.let {
                "${northern.captain.litechat.app.config.ApiConfig.BASE_URL}/litechat/api/v1/attachments/${it.id}/thumbnail"
            }
            val refFileAtt = refAtts.firstOrNull { a ->
                !a.mimeType.startsWith("image/") && !a.mimeType.startsWith("video/")
            }
            entity.toDomain(
                senderName = sender?.name ?: "Unknown",
                senderAvatar = sender?.avatar,
                attachments = attachmentsByMsg[entity.id] ?: emptyList(),
                reactions = reactionsByMsg[entity.id] ?: emptyList(),
                referenceMessageSenderName = refSender?.name,
                referenceMessageText = refMsg?.text,
                referenceMessageThumbnailUrl = refThumbnailUrl,
                referenceMessageFileName = refFileAtt?.originalFilename
            )
        }
    }

    suspend fun getMessageOffsetFromNewest(conversationId: String, messageId: String): Int {
        return messageDao.getMessageOffsetFromNewest(conversationId, messageId)
    }

    suspend fun getMessageCount(conversationId: String): Int {
        return messageDao.getMessageCount(conversationId)
    }

    suspend fun getAttachment(attachmentId: String) = messageDao.getAttachmentById(attachmentId)

    fun getMessagesFlow(conversationId: String, limit: Int = Int.MAX_VALUE): Flow<List<Message>> {
        return messageDao.getLatestMessagesFlow(conversationId, limit).map { entities ->
            if (entities.isEmpty()) return@map emptyList()
            val sorted = entities.sortedBy { it.id.toLongOrNull() ?: 0 }
            mapEntitiesToMessages(sorted)
        }
    }

    /**
     * Sync all messages for a conversation from the API into Room.
     * If we have cached messages, fetches only newer ones (after latest cached ID).
     * If no cache, paginates forward from 0 until all messages are fetched.
     */
    suspend fun syncAllMessages(conversationId: String) {
        val latestCachedId = getLatestCachedMessageId(conversationId) ?: "0"
        var after = latestCachedId
        while (true) {
            val response = api.getMessages(conversationId, after, 100)
            val messages = response.messages
            if (messages.isEmpty()) break
            storeMessages(messages)
            after = messages.last().id
            if (messages.size < 100) break
        }
    }

    suspend fun getLatestCachedMessageId(conversationId: String): String? {
        val id = messageDao.getLatestMessageId(conversationId) ?: return null
        return id.toString()
    }

    suspend fun loadMessages(conversationId: String, after: String = "0", limit: Int = 50): List<Message> {
        val response = api.getMessages(conversationId, after, limit)
        val messages = response.messages
        storeMessages(messages)
        return messages.map { dto ->
            val sender = userDao.getById(dto.senderId)
            dto.toDomain(sender?.name ?: "Unknown", sender?.avatar)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String? = null,
        referenceMessageId: String? = null,
        attachmentIds: List<Int>? = null
    ): Message {
        val response = api.sendMessage(
            conversationId,
            SendMessageRequestDto(text, referenceMessageId, attachmentIds)
        )
        storeMessages(listOf(response))
        val sender = userDao.getById(response.senderId)
        return response.toDomain(sender?.name ?: "Unknown", sender?.avatar)
    }

    suspend fun insertFromPoll(dto: MessageDto) {
        storeMessages(listOf(dto))
    }

    suspend fun insertReactionFromPoll(dto: ReactionResponseDto) {
        messageDao.insertReactions(
            listOf(
                ReactionEntity(
                    messageId = dto.messageId,
                    emoji = dto.emoji,
                    userId = dto.userId
                )
            )
        )
    }

    suspend fun addReaction(messageId: String, emoji: String, userId: Long) {
        api.addReaction(messageId, northern.captain.litechat.app.data.remote.dto.ReactionRequestDto(emoji))
        messageDao.insertReactions(
            listOf(ReactionEntity(messageId = messageId, emoji = emoji, userId = userId))
        )
    }

    suspend fun removeReaction(messageId: String, emoji: String, userId: Long) {
        api.removeReaction(messageId, northern.captain.litechat.app.data.remote.dto.ReactionRequestDto(emoji))
        messageDao.deleteReaction(messageId, emoji, userId)
    }

    private suspend fun storeMessages(messages: List<MessageDto>) {
        val entities = messages.map { it.toEntity() }
        messageDao.insertAll(entities)

        val attachments = messages.flatMap { msg ->
            msg.attachments.map { att ->
                AttachmentEntity(
                    id = att.id,
                    messageId = msg.id,
                    originalFilename = att.originalFilename,
                    mimeType = att.mimeType,
                    size = att.size,
                    hasThumbnail = att.hasThumbnail
                )
            }
        }
        if (attachments.isNotEmpty()) messageDao.insertAttachments(attachments)

        val reactions = messages.flatMap { msg ->
            msg.reactions.flatMap { group ->
                group.userIds.map { userId ->
                    ReactionEntity(
                        messageId = msg.id,
                        emoji = group.emoji,
                        userId = userId
                    )
                }
            }
        }
        if (reactions.isNotEmpty()) messageDao.insertReactions(reactions)
    }

    private fun MessageDto.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        text = text,
        referenceMessageId = referenceMessageId,
        createdAt = createdAt,
        delivered = delivered,
        readAt = readAt
    )

    private fun MessageDto.toDomain(senderName: String, senderAvatar: String? = null) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        text = text,
        referenceMessageId = referenceMessageId,
        attachments = attachments.map { att ->
            Attachment(
                id = att.id,
                originalFilename = att.originalFilename,
                mimeType = att.mimeType,
                size = att.size,
                hasThumbnail = att.hasThumbnail
            )
        },
        reactions = reactions.map { grp ->
            ReactionGroup(emoji = grp.emoji, userIds = grp.userIds)
        },
        createdAt = createdAt,
        delivered = delivered,
        readAt = readAt
    )

    private fun MessageEntity.toDomain(
        senderName: String,
        senderAvatar: String?,
        attachments: List<AttachmentEntity>,
        reactions: List<ReactionEntity>,
        referenceMessageSenderName: String? = null,
        referenceMessageText: String? = null,
        referenceMessageThumbnailUrl: String? = null,
        referenceMessageFileName: String? = null
    ) = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        senderAvatar = senderAvatar,
        text = text,
        referenceMessageId = referenceMessageId,
        referenceMessageSenderName = referenceMessageSenderName,
        referenceMessageText = referenceMessageText,
        referenceMessageThumbnailUrl = referenceMessageThumbnailUrl,
        referenceMessageFileName = referenceMessageFileName,
        attachments = attachments.map { att ->
            Attachment(
                id = att.id,
                originalFilename = att.originalFilename,
                mimeType = att.mimeType,
                size = att.size,
                hasThumbnail = att.hasThumbnail,
                thumbnailLocalPath = att.thumbnailLocalPath,
                originalLocalPath = att.originalLocalPath
            )
        },
        reactions = reactions.groupBy { it.emoji }.map { (emoji, reacts) ->
            ReactionGroup(emoji = emoji, userIds = reacts.map { it.userId })
        },
        createdAt = createdAt,
        delivered = delivered,
        readAt = readAt
    )
}
