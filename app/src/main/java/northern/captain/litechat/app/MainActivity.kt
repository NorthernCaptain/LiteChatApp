package northern.captain.litechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient
    @northern.captain.litechat.app.di.LongPollClient
    @Inject lateinit var longPollClient: okhttp3.OkHttpClient

    private val openConversationId = mutableStateOf<String?>(null)
    private val shareText = mutableStateOf<String?>(null)
    private val shareUris = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            LiteChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        openConversationId = openConversationId.value,
                        shareText = shareText.value,
                        shareUris = shareUris.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                shareText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                shareUris.value = listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                shareText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                shareUris.value = uris ?: emptyList()
            }
            else -> {
                val conversationId = intent.extras?.getString("conversationId")
                if (conversationId != null) {
                    openConversationId.value = conversationId
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                okHttpClient.connectionPool.evictAll()
                longPollClient.connectionPool.evictAll()
            }
            pollManager.start()
        }
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
