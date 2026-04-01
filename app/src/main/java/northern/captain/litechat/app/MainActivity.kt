package northern.captain.litechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import northern.captain.litechat.app.domain.polling.PollManager
import northern.captain.litechat.app.ui.navigation.NavGraph
import northern.captain.litechat.app.ui.theme.LiteChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pollManager: PollManager

    private val openConversationId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        openConversationId.value = intent?.extras?.getString("conversationId")
        enableEdgeToEdge()
        setContent {
            LiteChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(openConversationId = openConversationId.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val conversationId = intent.extras?.getString("conversationId")
        if (conversationId != null) {
            openConversationId.value = conversationId
        }
    }

    override fun onResume() {
        super.onResume()
        pollManager.start()
    }

    override fun onPause() {
        super.onPause()
        pollManager.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chat_messages",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
