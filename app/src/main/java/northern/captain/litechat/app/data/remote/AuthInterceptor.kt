package northern.captain.litechat.app.data.remote

import northern.captain.litechat.app.config.ApiConfig
import okhttp3.Authenticator
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = authManager.getToken()

        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}

@Singleton
class TokenAuthenticator @Inject constructor(
    private val authManager: AuthManager
) : Authenticator {

    @Volatile
    private var isRefreshing = false

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if we already retried once (avoid infinite loop)
        if (response.request.header("X-Retry-Auth") != null) return null

        // Don't try to refresh if we have no saved credentials
        val email = authManager.getEmail() ?: return null
        val password = authManager.getPassword() ?: return null

        synchronized(this) {
            // Check if another thread already refreshed the token
            val currentToken = authManager.getToken()
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && currentToken != requestToken) {
                // Token was already refreshed by another thread — retry with new token
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .header("X-Retry-Auth", "true")
                    .build()
            }

            if (isRefreshing) return null
            isRefreshing = true
        }

        try {
            val newToken = refreshToken(email, password) ?: return null
            authManager.saveToken(newToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("X-Retry-Auth", "true")
                .build()
        } finally {
            synchronized(this) { isRefreshing = false }
        }
    }

    private fun refreshToken(email: String, password: String): String? {
        return try {
            val client = OkHttpClient()
            val body = FormBody.Builder()
                .add("grant_type", "password")
                .add("username", email)
                .add("password", password)
                .add("client_id", ApiConfig.CLIENT_ID)
                .add("client_secret", ApiConfig.CLIENT_SECRET)
                .build()

            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/auth/login")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val json = response.body?.string() ?: return null
            // Simple JSON parsing for access_token
            val tokenRegex = """"access_token"\s*:\s*"([^"]+)"""".toRegex()
            tokenRegex.find(json)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }
}
