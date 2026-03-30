package northern.captain.litechat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.ui.auth.SignInScreen
import northern.captain.litechat.app.ui.camera.CameraScreen
import northern.captain.litechat.app.ui.chat.ChatScreen
import northern.captain.litechat.app.ui.chat.ChatViewModel
import northern.captain.litechat.app.ui.conversations.ConversationsScreen
import northern.captain.litechat.app.ui.media.FullscreenMediaScreen
import java.io.File

object Routes {
    const val SIGN_IN = "sign_in"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    const val MEDIA = "media/{attachmentId}?messageId={messageId}"
    const val CAMERA_PHOTO = "camera/photo"
    const val CAMERA_VIDEO = "camera/video"

    fun chat(conversationId: String) = "chat/$conversationId"
    fun media(attachmentId: String, messageId: String? = null) =
        if (messageId != null) "media/$attachmentId?messageId=$messageId"
        else "media/$attachmentId"
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
        ) { backStackEntry ->
            val viewModel: ChatViewModel = hiltViewModel()

            // Handle camera result
            val resultPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("camera_result_path", null)
                .collectAsState()

            LaunchedEffect(resultPath) {
                val path = resultPath ?: return@LaunchedEffect
                val mime = backStackEntry.savedStateHandle.get<String>("camera_result_mime") ?: "image/jpeg"
                viewModel.onCameraFileCaptured(File(path), mime)
                backStackEntry.savedStateHandle.remove<String>("camera_result_path")
                backStackEntry.savedStateHandle.remove<String>("camera_result_mime")
            }

            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onMediaClick = { attachmentId, messageId ->
                    navController.navigate(Routes.media(attachmentId, messageId))
                },
                onTakePhoto = { navController.navigate(Routes.CAMERA_PHOTO) },
                onRecordVideo = { navController.navigate(Routes.CAMERA_VIDEO) },
                viewModel = viewModel
            )
        }

        composable(Routes.CAMERA_PHOTO) {
            CameraScreen(
                mode = "photo",
                onResult = { file ->
                    if (file != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("camera_result_path", file.absolutePath)
                        navController.previousBackStackEntry?.savedStateHandle?.set("camera_result_mime", "image/jpeg")
                    }
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CAMERA_VIDEO) {
            CameraScreen(
                mode = "video",
                onResult = { file ->
                    if (file != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("camera_result_path", file.absolutePath)
                        navController.previousBackStackEntry?.savedStateHandle?.set("camera_result_mime", "video/mp4")
                    }
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.MEDIA,
            arguments = listOf(
                navArgument("attachmentId") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            FullscreenMediaScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
