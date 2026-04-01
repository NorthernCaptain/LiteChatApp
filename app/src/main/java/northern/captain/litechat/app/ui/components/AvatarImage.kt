package northern.captain.litechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import northern.captain.litechat.app.config.ApiConfig

@Composable
fun AvatarImage(
    userId: Long?,
    name: String,
    avatarFilename: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    if (userId != null && avatarFilename != null) {
        val avatarUrl = "${ApiConfig.BASE_URL}/litechat/api/v1/users/$userId/avatar?f=$avatarFilename"
        val showFallback = remember(avatarUrl) { mutableStateOf(false) }

        if (!showFallback.value) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { showFallback.value = true },
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            AvatarFallback(name = name, size = size, modifier = modifier)
        }
    } else {
        AvatarFallback(name = name, size = size, modifier = modifier)
    }
}

@Composable
fun AvatarFallback(
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFEA4335),
        Color(0xFFFBBC04), Color(0xFF9334E6), Color(0xFFE91E63)
    )
    val bgColor = colors[name.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }]

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium
        )
    }
}
