package northern.captain.litechat.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import northern.captain.litechat.app.domain.model.Conversation
import northern.captain.litechat.app.ui.components.AvatarImage
import northern.captain.litechat.app.ui.components.UnreadBadge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ConversationItem(
    conversation: Conversation,
    displayName: String,
    avatarUserId: Long?,
    avatarFilename: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                userId = avatarUserId,
                name = displayName,
                avatarFilename = avatarFilename,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    conversation.lastMessage?.createdAt?.let { time ->
                        Text(
                            text = formatConversationTime(time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.lastMessage?.let { msg ->
                            msg.text ?: "\uD83D\uDCCE Attachment"
                        } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadBadge(count = conversation.unreadCount)
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

fun formatConversationTime(isoTime: String): String {
    return try {
        val instant = Instant.parse(isoTime)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val msgDate = zoned.toLocalDate()
        val today = LocalDate.now()

        when (msgDate) {
            today -> zoned.toLocalTime().format(
                java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            )
            today.minusDays(1) -> "yesterday"
            else -> msgDate.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (_: Exception) {
        ""
    }
}

fun formatRelativeTime(isoTime: String): String {
    return try {
        val instant = Instant.parse(isoTime)
        val now = Instant.now()
        val minutes = ChronoUnit.MINUTES.between(instant, now)
        val hours = ChronoUnit.HOURS.between(instant, now)
        val msgDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()

        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            hours < 24 && msgDate == today -> "${hours}h"
            msgDate == today.minusDays(1) -> "yesterday"
            else -> msgDate.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (_: Exception) {
        ""
    }
}
