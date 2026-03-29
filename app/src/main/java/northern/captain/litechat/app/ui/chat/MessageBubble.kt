package northern.captain.litechat.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import northern.captain.litechat.app.config.ApiConfig
import northern.captain.litechat.app.domain.model.Attachment
import northern.captain.litechat.app.domain.model.Message
import northern.captain.litechat.app.domain.model.ReactionGroup
import northern.captain.litechat.app.ui.components.AvatarImage
import northern.captain.litechat.app.ui.conversations.formatRelativeTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    isFirstInGroup: Boolean,
    isGroupConvo: Boolean,
    currentUserId: Long,
    onReactionClick: (String, String) -> Unit,
    onLongPress: (Message, Offset) -> Unit,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var bubbleBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    val doLongPress = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Pass the top-center of the bubble as the touch offset
        onLongPress(message, Offset(bubbleBounds.center.x, bubbleBounds.top))
    }

    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val bubbleColor = if (isOwnMessage) {
        if (isLightTheme) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.primaryContainer
    } else {
        if (isLightTheme) Color(0xFFFFF9C4) else MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOwnMessage) {
        if (isLightTheme) Color.Black.copy(alpha = 0.87f) else Color.White.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isOwnMessage) 48.dp else 8.dp,
                end = if (isOwnMessage) 8.dp else 48.dp,
                top = if (isFirstInGroup) 8.dp else 2.dp,
                bottom = 2.dp
            ),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for other's messages
        if (!isOwnMessage) {
            if (isFirstInGroup) {
                AvatarImage(
                    userId = message.senderId,
                    name = message.senderName,
                    avatarFilename = message.senderAvatar,
                    size = 32.dp
                )
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(min = 80.dp, max = 280.dp)
        ) {
            // Sender name in group convos
            if (!isOwnMessage && isGroupConvo && isFirstInGroup) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                tonalElevation = 0.5.dp,
                modifier = Modifier.onGloballyPositioned { coords ->
                    bubbleBounds = coords.boundsInWindow()
                }
            ) {
                Column(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { doLongPress() }
                        )
                        .padding(2.dp)
                ) {
                    // Attachment thumbnails
                    if (message.attachments.isNotEmpty()) {
                        AttachmentThumbnails(
                            attachments = message.attachments,
                            onMediaClick = onMediaClick,
                            onLongClick = { doLongPress() }
                        )
                    }

                    // Reply preview
                    if (message.referenceMessageId != null) {
                        Row(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 6.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reply to #${message.referenceMessageId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Message text
                    if (!message.text.isNullOrBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Timestamp + Reactions row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.reactions.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                message.reactions.forEach { reaction ->
                                    val hasReacted = currentUserId in reaction.userIds
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (hasReacted)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        modifier = Modifier
                                            .then(
                                                if (hasReacted) Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(10.dp)
                                                ) else Modifier
                                            )
                                            .clickable { onReactionClick(message.id, reaction.emoji) }
                                    ) {
                                        Text(
                                            text = "${reaction.emoji} ${reaction.count}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Text(
                            text = formatMessageTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentThumbnails(
    attachments: List<Attachment>,
    onMediaClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val imageAttachments = attachments.filter {
        it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
    }

    if (imageAttachments.size == 1) {
        val att = imageAttachments.first()
        ThumbnailWithPlayIcon(
            attachment = att,
            onMediaClick = onMediaClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .clip(RoundedCornerShape(14.dp))
        )
    } else if (imageAttachments.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(2.dp)
        ) {
            items(imageAttachments) { att ->
                ThumbnailWithPlayIcon(
                    attachment = att,
                    onMediaClick = onMediaClick,
                    onLongClick = onLongClick,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }

    // Non-image attachments as file chips
    val fileAttachments = attachments.filter {
        !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/")
    }
    fileAttachments.forEach { att ->
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .combinedClickable(
                    onClick = { onMediaClick(att.id) },
                    onLongClick = onLongClick
                )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = att.originalFilename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatFileSize(att.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ReactionChips(
    reactions: List<ReactionGroup>,
    currentUserId: Long,
    onReactionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            val hasReacted = currentUserId in reaction.userIds
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .then(
                        if (hasReacted) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .clickable { onReactionClick(reaction.emoji) }
            ) {
                Text(
                    text = "${reaction.emoji} ${reaction.count}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailWithPlayIcon(
    attachment: Attachment,
    onMediaClick: (String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = if (attachment.hasThumbnail) {
        "${ApiConfig.BASE_URL}/litechat/api/v1/attachments/${attachment.id}/thumbnail"
    } else null
    val isVideo = attachment.mimeType.startsWith("video/")

    Box(
        modifier = modifier.combinedClickable(
            onClick = { onMediaClick(attachment.id) },
            onLongClick = onLongClick
        ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = attachment.originalFilename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun formatMessageTime(isoTime: String): String {
    return try {
        val instant = java.time.Instant.parse(isoTime)
        val localTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val formatter = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        localTime.format(formatter)
    } catch (_: Exception) {
        ""
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
