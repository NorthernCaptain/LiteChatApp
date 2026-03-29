package northern.captain.litechat.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAutoLogging: Boolean = false,
    val autoLoginFailed: Boolean = false,
    val signInSuccess: Boolean = false
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    init {
        if (authManager.hasSavedCredentials()) {
            attemptAutoLogin()
        }
    }

    private fun attemptAutoLogin() {
        _uiState.update { it.copy(isAutoLogging = true, isLoading = true, autoLoginFailed = false) }
        viewModelScope.launch {
            val result = authRepository.autoLogin()
            if (result.isSuccess) {
                _uiState.update { it.copy(signInSuccess = true, isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(isAutoLogging = false, isLoading = false, autoLoginFailed = true)
                }
            }
        }
    }

    fun onRetryAutoLogin() {
        attemptAutoLogin()
    }

    fun onGoToSignIn() {
        authManager.clear()
        _uiState.update { it.copy(autoLoginFailed = false, isAutoLogging = false) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onSignInClick() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "empty_fields") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.login(state.email, state.password)
            if (result.isSuccess) {
                _uiState.update { it.copy(signInSuccess = true, isLoading = false) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = "invalid_credentials")
                }
            }
        }
    }
}
