package northern.captain.litechat.app.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.ui.auth.SignInScreen
import androidx.camera.core.CameraSelector
import northern.captain.litechat.app.ui.camera.AvatarCropScreen
import northern.captain.litechat.app.ui.camera.CameraScreen
import northern.captain.litechat.app.ui.chat.ChatScreen
import northern.captain.litechat.app.ui.chat.ChatViewModel
import northern.captain.litechat.app.ui.conversations.ConversationsScreen
import northern.captain.litechat.app.ui.conversations.ConversationsViewModel
import northern.captain.litechat.app.ui.media.FullscreenMediaScreen
import java.io.File

object Routes {
    const val SIGN_IN = "sign_in"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    const val MEDIA = "media/{attachmentId}?messageId={messageId}"
    const val CAMERA_PHOTO = "camera/photo"
    const val CAMERA_VIDEO = "camera/video"
    const val AVATAR_CAMERA = "camera/avatar"
    const val AVATAR_CROP = "avatar_crop?filePath={filePath}"

    fun chat(conversationId: String) = "chat/$conversationId"
    fun media(attachmentId: String, messageId: String? = null) =
        if (messageId != null) "media/$attachmentId?messageId=$messageId"
        else "media/$attachmentId"
    fun avatarCrop(filePath: String) = "avatar_crop?filePath=${android.net.Uri.encode(filePath)}"
}

@Composable
fun NavGraph(authManager: AuthManager? = null, openConversationId: String? = null) {
    val navController = rememberNavController()
    val startDestination = if (authManager?.isLoggedIn() == true || authManager?.hasSavedCredentials() == true) {
        Routes.SIGN_IN // still go to sign_in for auto-login flow
    } else {
        Routes.SIGN_IN
    }

    // Handle FCM notification tap — navigate to specific conversation
    var pendingConversationId by remember { mutableStateOf(openConversationId) }

    LaunchedEffect(openConversationId) {
        if (openConversationId != null) {
            pendingConversationId = openConversationId
            // If already past sign-in, navigate directly
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != null && currentRoute != Routes.SIGN_IN) {
                navController.navigate(Routes.chat(openConversationId)) {
                    popUpTo(Routes.CONVERSATIONS)
                }
                pendingConversationId = null
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                    // If launched from notification, navigate to the chat
                    pendingConversationId?.let { id ->
                        navController.navigate(Routes.chat(id))
                        pendingConversationId = null
                    }
                }
            )
        }

        composable(Routes.CONVERSATIONS) { backStackEntry ->
            val viewModel: ConversationsViewModel = hiltViewModel()

            // Handle camera result for avatar → navigate to crop
            val cameraResultPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("camera_result_path", null)
                .collectAsState()

            LaunchedEffect(cameraResultPath) {
                val path = cameraResultPath ?: return@LaunchedEffect
                backStackEntry.savedStateHandle.remove<String>("camera_result_path")
                navController.navigate(Routes.avatarCrop(path))
            }

            // Handle avatar crop result → upload
            val avatarResultPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("avatar_result_path", null)
                .collectAsState()

            LaunchedEffect(avatarResultPath) {
                val path = avatarResultPath ?: return@LaunchedEffect
                viewModel.uploadAvatar(File(path))
                backStackEntry.savedStateHandle.remove<String>("avatar_result_path")
            }

            ConversationsScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onSignOut = {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onTakeAvatarPhoto = { navController.navigate(Routes.AVATAR_CAMERA) },
                onCropAvatar = { filePath -> navController.navigate(Routes.avatarCrop(filePath)) },
                viewModel = viewModel
            )
        }

        composable(Routes.AVATAR_CAMERA) {
            CameraScreen(
                mode = "photo",
                defaultLensFacing = CameraSelector.LENS_FACING_FRONT,
                onResult = { file ->
                    if (file != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("camera_result_path", file.absolutePath)
                    }
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
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

        composable(
            route = Routes.AVATAR_CROP,
            arguments = listOf(navArgument("filePath") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            AvatarCropScreen(
                filePath = filePath,
                onResult = { file ->
                    if (file != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("avatar_result_path", file.absolutePath)
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
