package northern.captain.litechat.app.domain.polling

import android.content.Context
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.local.dao.MessageDao
import northern.captain.litechat.app.data.remote.dto.AckRequestDto
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
    private val regularApi: LiteChatApi,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val messageDao: MessageDao,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var pollJob: Job? = null
    private var lastEventId: String = "0"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var isRunning = false

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId

    private val _newMessages = MutableSharedFlow<Message>(extraBufferCapacity = 50)
    val newMessages: SharedFlow<Message> = _newMessages

    private val _newReactionEvent = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val newReactionEvent: SharedFlow<String> = _newReactionEvent

    data class TypingEvent(val conversationId: String, val userId: Long, val userName: String, val active: Boolean)
    private val _typingEvent = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 50)
    val typingEvent: SharedFlow<TypingEvent> = _typingEvent

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun start() {
        if (!authManager.isLoggedIn()) return
        isRunning = true
        _isConnected.value = true
        registerNetworkCallback()
        startPollLoop()
    }

    fun stop() {
        isRunning = false
        unregisterNetworkCallback()
        pollJob?.cancel()
        pollJob = null
        _isConnected.value = false
    }

    private fun startPollLoop() {
        if (pollJob?.isActive == true) return

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
                                    playNotificationSound()
                                }
                                // Send delivery ack (fire-and-forget)
                                scope.launch {
                                    try { regularApi.acknowledgeDelivery(event.conversationId, AckRequestDto(msg.id)) } catch (_: Exception) {}
                                }
                            }
                            "reaction" -> {
                                val reaction = event.reaction ?: continue
                                messageRepository.insertReactionFromPoll(reaction)
                                _newReactionEvent.tryEmit(reaction.messageId)
                            }
                            "typing" -> {
                                val meta = event.meta ?: continue
                                val userId = (meta["userId"] as? Number)?.toLong() ?: continue
                                val userName = meta["name"] as? String ?: continue
                                val active = meta["active"] as? Boolean ?: true
                                _typingEvent.tryEmit(TypingEvent(event.conversationId, userId, userName, active))
                            }
                            "delivery" -> {
                                val meta = event.meta ?: continue
                                val messageId = meta["messageId"] as? String ?: continue
                                messageDao.markDelivered(event.conversationId, messageId)
                            }
                            "read" -> {
                                val meta = event.meta ?: continue
                                val messageId = meta["messageId"] as? String ?: continue
                                messageDao.markRead(event.conversationId, messageId)
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

    private fun restart() {
        pollJob?.cancel()
        pollJob = null
        startPollLoop()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isRunning) {
                    _isConnected.value = true
                    restart()
                }
            }

            override fun onLost(network: Network) {
                _isConnected.value = false
                if (isRunning) {
                    restart()
                }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        networkCallback = null
    }

    private fun playNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) {}
    }

    fun setActiveConversation(id: String?) {
        _activeConversationId.value = id
    }
}
