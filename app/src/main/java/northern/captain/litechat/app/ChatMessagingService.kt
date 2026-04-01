package northern.captain.litechat.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.data.remote.dto.FcmTokenRequestDto
import javax.inject.Inject

@AndroidEntryPoint
class ChatMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var api: LiteChatApi

    override fun onNewToken(token: String) {
        authManager.saveFcmToken(token)
        if (authManager.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    api.registerFcmToken(FcmTokenRequestDto(token))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val conversationId = data["conversationId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) {
                putExtra("conversationId", conversationId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "chat_messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = conversationId?.hashCode() ?: System.currentTimeMillis().toInt()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
