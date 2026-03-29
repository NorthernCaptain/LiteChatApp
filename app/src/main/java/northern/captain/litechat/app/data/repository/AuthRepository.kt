package northern.captain.litechat.app.data.repository

import northern.captain.litechat.app.config.ApiConfig
import northern.captain.litechat.app.data.local.LiteChatDatabase
import northern.captain.litechat.app.data.remote.AuthApi
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.remote.LiteChatApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val liteChatApi: LiteChatApi,
    private val authManager: AuthManager,
    private val database: LiteChatDatabase
) {
    suspend fun login(email: String, password: String): Result<Long> {
        return try {
            val response = authApi.login(
                username = email,
                password = password,
                clientId = ApiConfig.CLIENT_ID,
                clientSecret = ApiConfig.CLIENT_SECRET
            )
            authManager.saveToken(response.accessToken)
            authManager.saveCredentials(email, password)

            try {
                val me = liteChatApi.getMe()
                authManager.saveUserId(me.userId)
            } catch (_: Exception) {
                // /users/me failed but login succeeded — userId will be resolved later
            }

            Result.success(authManager.getUserId())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun hasSavedCredentials(): Boolean = authManager.hasSavedCredentials()

    suspend fun autoLogin(): Result<Long> {
        val email = authManager.getEmail() ?: return Result.failure(Exception("No saved credentials"))
        val password = authManager.getPassword() ?: return Result.failure(Exception("No saved credentials"))
        return login(email, password)
    }

    suspend fun signOut() {
        authManager.clear()
        database.userDao().deleteAll()
        database.conversationDao().deleteAll()
        database.conversationDao().deleteAllMembers()
        database.messageDao().deleteAll()
        database.messageDao().deleteAllAttachments()
        database.messageDao().deleteAllReactions()
    }
}
