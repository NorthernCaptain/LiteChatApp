package northern.captain.litechat.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.repository.AuthRepository
import northern.captain.litechat.app.data.repository.ConversationRepository
import northern.captain.litechat.app.data.repository.UserRepository
import northern.captain.litechat.app.domain.model.Conversation
import northern.captain.litechat.app.domain.model.User
import northern.captain.litechat.app.domain.polling.PollManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationsUiState(
    val currentUserName: String = "",
    val conversations: List<Conversation> = emptyList(),
    val allUsers: List<User> = emptyList(),
    val usersWithoutConvo: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val creatingConvoForUserId: Long? = null,
    val error: String? = null,
    val signedOut: Boolean = false,
    val navigateToChat: String? = null
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val authManager: AuthManager,
    private val pollManager: PollManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()
    val isConnected: StateFlow<Boolean> = pollManager.isConnected

    private val currentUserId: Long get() = authManager.getUserId()

    init {
        viewModelScope.launch {
            try {
                userRepository.refreshUsers()
                conversationRepository.refreshConversations()
            } catch (_: Exception) {}
            _uiState.update { it.copy(isLoading = false) }
        }

        viewModelScope.launch {
            combine(
                conversationRepository.getConversations(),
                userRepository.getUsers()
            ) { conversations, users ->
                val currentUserName = users.find { it.userId == currentUserId }?.name ?: ""
                val directUserIds = conversations
                    .filter { it.type == "direct" }
                    .flatMap { convo -> convo.members.map { it.userId } }
                    .filter { it != currentUserId }
                    .toSet()

                val usersWithoutConvo = users.filter {
                    it.userId != currentUserId && it.userId !in directUserIds
                }

                _uiState.update {
                    it.copy(
                        currentUserName = currentUserName,
                        conversations = conversations,
                        allUsers = users,
                        usersWithoutConvo = usersWithoutConvo
                    )
                }
            }.collect()
        }
    }

    fun onConversationClick(conversationId: String) {
        _uiState.update { it.copy(navigateToChat = conversationId) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToChat = null) }
    }

    fun onUserClick(userId: Long) {
        _uiState.update { it.copy(creatingConvoForUserId = userId) }
        viewModelScope.launch {
            try {
                val convo = conversationRepository.createDirectConversation(userId)
                _uiState.update {
                    it.copy(creatingConvoForUserId = null, navigateToChat = convo.id)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(creatingConvoForUserId = null, error = e.message)
                }
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(signedOut = true) }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                userRepository.refreshUsers()
                conversationRepository.refreshConversations()
            } catch (_: Exception) {}
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun getConversationDisplayName(conversation: Conversation): String {
        if (conversation.type == "group") return conversation.name ?: "Group"
        val otherMember = conversation.members.firstOrNull { it.userId != currentUserId }
        return otherMember?.let { member ->
            _uiState.value.allUsers.find { it.userId == member.userId }?.name
        } ?: "Chat"
    }

    fun getOtherUser(conversation: Conversation): User? {
        if (conversation.type != "direct") return null
        val otherMemberId = conversation.members.firstOrNull { it.userId != currentUserId }?.userId ?: return null
        return _uiState.value.allUsers.find { it.userId == otherMemberId }
    }
}
