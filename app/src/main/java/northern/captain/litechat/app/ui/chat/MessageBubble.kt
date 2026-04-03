package northern.captain.litechat.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.util.Patterns
import androidx.compose.foundation.text.ClickableText
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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import northern.captain.litechat.app.config.ApiConfig
import northern.captain.litechat.app.domain.model.Attachment
import northern.captain.litechat.app.domain.model.Message
import northern.captain.litechat.app.domain.model.ReactionGroup
import northern.captain.litechat.app.ui.components.AvatarImage
import northern.captain.litechat.app.ui.conversations.formatRelativeTime

@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    isFirstInGroup: Boolean,
    isGroupConvo: Boolean,
    currentUserId: Long,
    onReactionClick: (String, String) -> Unit,
    onLongPress: (Message, Offset) -> Unit,
    onMediaClick: (attachmentId: String, messageId: String) -> Unit,
    onFileClick: (attachmentId: String, filename: String, mimeType: String) -> Unit,
    onScrollToMessage: (messageId: String) -> Unit = {},
    isHighlighted: Boolean = false,
    downloadingAttachmentId: String? = null,
    downloadProgress: Float = 0f,
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

    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            repeat(2) {
                highlightAlpha.animateTo(0.2f, androidx.compose.animation.core.tween(300))
                highlightAlpha.animateTo(0f, androidx.compose.animation.core.tween(300))
            }
            highlightAlpha.snapTo(0f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (highlightAlpha.value > 0f) {
                    drawRect(highlightColor.copy(alpha = highlightAlpha.value))
                }
            }
            .clickable { doLongPress() }
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
            modifier = Modifier.widthIn(min = 80.dp, max = 310.dp)
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
                        .clickable { doLongPress() }
                        .padding(2.dp)
                ) {
                    // Attachment thumbnails
                    if (message.attachments.isNotEmpty()) {
                        AttachmentThumbnails(
                            attachments = message.attachments,
                            messageId = message.id,
                            onMediaClick = onMediaClick,
                            onFileClick = onFileClick,
                            downloadingAttachmentId = downloadingAttachmentId,
                            downloadProgress = downloadProgress
                        )
                    }

                    // Reply preview
                    if (message.referenceMessageId != null) {
                        Row(
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp, top = 6.dp)
                                .clickable { onScrollToMessage(message.referenceMessageId) }
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = message.referenceMessageSenderName ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                if (!message.referenceMessageText.isNullOrBlank()) {
                                    Text(
                                        text = message.referenceMessageText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (message.referenceMessageFileName != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = textColor.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = message.referenceMessageFileName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (message.referenceMessageThumbnailUrl != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AsyncImage(
                                    model = message.referenceMessageThumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }

                    // Message text
                    if (!message.text.isNullOrBlank()) {
                        LinkableText(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Reactions
                    if (message.reactions.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp)
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
                    }
                    // Timestamp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = formatMessageTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                        if (isOwnMessage) {
                            val tickIcon = when {
                                message.readAt -> Icons.Default.DoneAll
                                message.delivered -> Icons.Default.Done
                                else -> null
                            }
                            if (tickIcon != null) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    tickIcon,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumbnails(
    attachments: List<Attachment>,
    messageId: String,
    onMediaClick: (attachmentId: String, messageId: String) -> Unit,
    onFileClick: (attachmentId: String, filename: String, mimeType: String) -> Unit,
    downloadingAttachmentId: String?,
    downloadProgress: Float
) {
    val imageAttachments = attachments.filter {
        it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
    }

    if (imageAttachments.size == 1) {
        val att = imageAttachments.first()
        ThumbnailWithPlayIcon(
            attachment = att,
            messageId = messageId,
            onMediaClick = onMediaClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 306.dp)
                .clip(RoundedCornerShape(14.dp))
        )
    } else if (imageAttachments.size > 1) {
        val gridWidth = 306 // dp, available width inside padding
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(2.dp)
        ) {
            imageAttachments.chunked(3).forEach { row ->
                val thumbSize = (gridWidth - (row.size - 1) * 4) / row.size
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { att ->
                        ThumbnailWithPlayIcon(
                            attachment = att,
                            messageId = messageId,
                            onMediaClick = onMediaClick,
                            modifier = Modifier
                                .size(thumbSize.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        }
    }

    // Non-image attachments as file chips
    val fileAttachments = attachments.filter {
        !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/")
    }
    fileAttachments.forEach { att ->
        val isDownloading = downloadingAttachmentId == att.id
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable {
                    if (!isDownloading) onFileClick(att.id, att.originalFilename, att.mimeType)
                }
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloading) {
                    if (downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                val dotIndex = att.originalFilename.lastIndexOf('.')
                if (dotIndex > 0) {
                    val namePart = att.originalFilename.substring(0, dotIndex)
                    val extPart = att.originalFilename.substring(dotIndex)
                    Text(
                        text = namePart,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = extPart,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = att.originalFilename,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
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

@Composable
private fun ThumbnailWithPlayIcon(
    attachment: Attachment,
    messageId: String,
    onMediaClick: (attachmentId: String, messageId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailUrl = if (attachment.hasThumbnail) {
        "${ApiConfig.BASE_URL}/litechat/api/v1/attachments/${attachment.id}/thumbnail"
    } else null
    val isVideo = attachment.mimeType.startsWith("video/")

    Box(
        modifier = modifier.clickable { onMediaClick(attachment.id, messageId) },
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

private val linkPattern = Regex(
    "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)|(mailto:[\\w.+-]+@[\\w.-]+)|(tel:[+\\d\\-().]+)"
)

@Composable
private fun LinkableText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val hasLinks = remember(text) { linkPattern.containsMatchIn(text) }

    if (!hasLinks) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier
        )
    } else {
        val annotated = remember(text, color, linkColor) {
            buildAnnotatedString {
                var lastEnd = 0
                linkPattern.findAll(text).forEach { match ->
                    if (match.range.first > lastEnd) {
                        withStyle(SpanStyle(color = color)) {
                            append(text.substring(lastEnd, match.range.first))
                        }
                    }
                    val url = match.value
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(url)
                    }
                    pop()
                    lastEnd = match.range.last + 1
                }
                if (lastEnd < text.length) {
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(lastEnd))
                    }
                }
            }
        }

        ClickableText(
            text = annotated,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
            }
        )
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
