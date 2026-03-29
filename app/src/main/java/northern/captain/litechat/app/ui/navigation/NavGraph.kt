package northern.captain.litechat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.ui.auth.SignInScreen
import northern.captain.litechat.app.ui.chat.ChatScreen
import northern.captain.litechat.app.ui.conversations.ConversationsScreen
import northern.captain.litechat.app.ui.media.FullscreenMediaScreen

object Routes {
    const val SIGN_IN = "sign_in"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    const val MEDIA = "media/{attachmentId}"

    fun chat(conversationId: String) = "chat/$conversationId"
    fun media(attachmentId: String) = "media/$attachmentId"
}

@Composable
fun NavGraph(authManager: AuthManager? = null) {
    val navController = rememberNavController()
    val startDestination = if (authManager?.isLoggedIn() == true || authManager?.hasSavedCredentials() == true) {
        Routes.SIGN_IN // still go to sign_in for auto-login flow
    } else {
        Routes.SIGN_IN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onSignOut = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onMediaClick = { attachmentId ->
                    navController.navigate(Routes.media(attachmentId))
                }
            )
        }

        composable(
            route = Routes.MEDIA,
            arguments = listOf(navArgument("attachmentId") { type = NavType.StringType })
        ) {
            FullscreenMediaScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
