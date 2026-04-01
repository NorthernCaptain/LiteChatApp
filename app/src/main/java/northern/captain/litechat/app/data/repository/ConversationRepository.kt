package northern.captain.litechat.app.data.repository

import northern.captain.litechat.app.data.local.dao.ConversationDao
import northern.captain.litechat.app.data.local.dao.UserDao
import northern.captain.litechat.app.data.local.entity.ConversationEntity
import northern.captain.litechat.app.data.local.entity.ConversationMemberEntity
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.remote.dto.ConversationDto
import northern.captain.litechat.app.data.remote.dto.CreateConversationRequestDto
import northern.captain.litechat.app.domain.model.Conversation
import northern.captain.litechat.app.domain.model.ConversationMember
import northern.captain.litechat.app.domain.model.LastMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val api: LiteChatApi,
    private val conversationDao: ConversationDao,
    private val userDao: UserDao
) {
    fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAll().combine(conversationDao.getAllMembers()) { convos, members ->
            val membersByConvo = members.groupBy { it.conversationId }
            convos.map { entity ->
                entity.toDomain(membersByConvo[entity.id] ?: emptyList())
            }
        }
    }

    suspend fun refreshConversations() {
        val response = api.getConversations()
        val entities = response.conversations.map { it.toEntity() }
        conversationDao.upsertAll(entities)

        val memberEntities = response.conversations.flatMap { dto ->
            dto.members.map { member ->
                ConversationMemberEntity(
                    conversationId = dto.id,
                    userId = member.userId,
                    joinedAt = member.joinedAt
                )
            }
        }
        conversationDao.insertMembers(memberEntities)
    }

    suspend fun createDirectConversation(userId: Long): Conversation {
        val response = api.createConversation(
            CreateConversationRequestDto(
                type = "direct",
                memberIds = listOf(userId)
            )
        )
        val entity = response.toEntity()
        conversationDao.upsert(entity)
        val memberEntities = response.members.map { member ->
            ConversationMemberEntity(
                conversationId = response.id,
                userId = member.userId,
                joinedAt = member.joinedAt
            )
        }
        conversationDao.insertMembers(memberEntities)
        return entity.toDomain(memberEntities)
    }

    suspend fun getConversation(id: String): Conversation? {
        val entity = conversationDao.getById(id) ?: return null
        val members = conversationDao.getMembers(id)
        return entity.toDomain(members)
    }

    suspend fun incrementUnread(conversationId: String) {
        conversationDao.incrementUnread(conversationId)
    }

    suspend fun resetUnread(conversationId: String) {
        conversationDao.resetUnread(conversationId)
    }

    suspend fun updateLastMessage(conversationId: String, messageId: String, text: String?, senderId: Long, createdAt: String) {
        conversationDao.updateLastMessage(conversationId, messageId, text, senderId, createdAt)
    }

    private fun ConversationDto.toEntity() = ConversationEntity(
        id = id,
        type = type,
        name = name,
        updatedAt = updatedAt,
        lastMessageId = lastMessage?.id,
        lastMessageText = lastMessage?.text,
        lastMessageSenderId = lastMessage?.senderId,
        lastMessageCreatedAt = lastMessage?.createdAt
    )

    private fun ConversationEntity.toDomain(members: List<ConversationMemberEntity>) = Conversation(
        id = id,
        type = type,
        name = name,
        members = members.map { ConversationMember(it.userId, it.joinedAt) },
        lastMessage = if (lastMessageId != null) LastMessage(
            id = lastMessageId,
            senderId = lastMessageSenderId ?: 0,
            text = lastMessageText,
            createdAt = lastMessageCreatedAt ?: ""
        ) else null,
        updatedAt = updatedAt,
        unreadCount = unreadCount
    )
}
