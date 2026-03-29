package northern.captain.litechat.app.domain.polling

import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.remote.dto.PollRequestDto
import northern.captain.litechat.app.data.repository.ConversationRepository
import northern.captain.litechat.app.data.repository.MessageRepository
import northern.captain.litechat.app.di.ApplicationScope
import northern.captain.litechat.app.di.LongPollApi
import northern.captain.litechat.app.domain.model.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PollManager @Inject constructor(
    @LongPollApi private val api: LiteChatApi,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val authManager: AuthManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var pollJob: Job? = null
    private var lastEventId: String = "0"

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId

    private val _newMessages = MutableSharedFlow<Message>(extraBufferCapacity = 50)
    val newMessages: SharedFlow<Message> = _newMessages

    private val _newReactionEvent = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val newReactionEvent: SharedFlow<String> = _newReactionEvent

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun start() {
        if (!authManager.isLoggedIn()) return
        if (pollJob?.isActive == true) return
        _isConnected.value = true

        pollJob = scope.launch {
            while (isActive) {
                try {
                    val response = api.poll(PollRequestDto(after = lastEventId))
                    _isConnected.value = true
                    for (event in response.events) {
                        when (event.type) {
                            "message" -> {
                                val msg = event.message ?: continue
                                messageRepository.insertFromPoll(msg)
                                conversationRepository.updateLastMessage(
                                    event.conversationId,
                                    msg.id,
                                    msg.text,
                                    msg.senderId,
                                    msg.createdAt
                                )
                                if (event.conversationId == _activeConversationId.value) {
                                    // Message will appear via Room Flow
                                } else {
                                    conversationRepository.incrementUnread(event.conversationId)
                                }
                            }
                            "reaction" -> {
                                val reaction = event.reaction ?: continue
                                messageRepository.insertReactionFromPoll(reaction)
                                _newReactionEvent.tryEmit(reaction.messageId)
                            }
                        }
                        if (event.pendingId > lastEventId) {
                            lastEventId = event.pendingId
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _isConnected.value = false
                    delay(2000)
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        _isConnected.value = false
    }

    fun setActiveConversation(id: String?) {
        _activeConversationId.value = id
    }
}
