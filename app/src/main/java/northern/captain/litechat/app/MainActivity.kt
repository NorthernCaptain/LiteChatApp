package northern.captain.litechat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import northern.captain.litechat.app.domain.polling.PollManager
import northern.captain.litechat.app.ui.navigation.NavGraph
import northern.captain.litechat.app.ui.theme.LiteChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pollManager: PollManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiteChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
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
}
