package northern.captain.litechat.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private val REACTION_EMOJIS_ROW1 = listOf(
    "\uD83D\uDC4D", // thumbsup
    "\u2764\uFE0F", // heart
    "\uD83D\uDE02", // joy
    "\uD83D\uDE2E", // open mouth
    "\uD83D\uDE22", // cry
    "\uD83D\uDD25", // fire
    "\uD83D\uDC4F", // clap
    "\uD83C\uDF89"  // party
)

private val REACTION_EMOJIS_ROW2 = listOf(
    "\uD83D\uDC4E", // thumbsdown
    "\uD83D\uDE0D", // heart eyes
    "\uD83E\uDD23", // rofl
    "\uD83E\uDD14", // thinking
    "\uD83D\uDE31", // scream
    "\uD83D\uDE4C", // raised hands
    "\uD83D\uDCAF", // 100
    "\uD83D\uDE09"  // wink
)

private val PICKER_HEIGHT_DP = 112

@Composable
fun ReactionPicker(
    touchOffset: Offset,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val pickerHeightPx = with(density) { PICKER_HEIGHT_DP.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }

    // Prefer above the touch point; if not enough space, go below
    val yOffset = if (touchOffset.y - pickerHeightPx - margin > 0) {
        (touchOffset.y - pickerHeightPx - margin).toInt()
    } else {
        (touchOffset.y + margin).toInt()
    }

    // Center horizontally on screen
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val pickerWidthPx = with(density) { 400.dp.toPx() }
    val xOffset = ((screenWidthPx - pickerWidthPx) / 2).toInt().coerceAtLeast(0)

    Popup(
        offset = IntOffset(xOffset, yOffset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf(REACTION_EMOJIS_ROW1, REACTION_EMOJIS_ROW2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { emoji ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        onEmojiSelected(emoji)
                                        onDismiss()
                                    }
                            ) {
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
