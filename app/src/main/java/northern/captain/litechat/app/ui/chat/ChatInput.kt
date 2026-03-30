package northern.captain.litechat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import northern.captain.litechat.app.R
import northern.captain.litechat.app.domain.model.Message

@Composable
fun ChatInput(
    inputText: String,
    replyToMessage: Message?,
    pendingAttachments: List<PendingAttachment>,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentsPicked: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onCancelReply: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecordVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onAttachmentsPicked(uris)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onAttachmentsPicked(uris)
    }

    val anyUploading = pendingAttachments.any { it.isUploading }
    val canSend = (inputText.isNotBlank() || pendingAttachments.isNotEmpty()) && !anyUploading

    Column(modifier = modifier) {
        // Reply bar
        AnimatedVisibility(
            visible = replyToMessage != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            replyToMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = msg.senderName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = msg.text ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onCancelReply) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Pending attachments
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(pendingAttachments, key = { it.localId }) { attachment ->
                    val isImage = attachment.mimeType.startsWith("image/")
                    val hasServerThumbnail = attachment.thumbnailUrl != null
                    Box(modifier = Modifier.size(60.dp)) {
                        when {
                            hasServerThumbnail -> {
                                AsyncImage(
                                    model = attachment.thumbnailUrl,
                                    contentDescription = attachment.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            isImage -> {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = attachment.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            else -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = attachment.filename,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        if (attachment.isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .clickable { onRemoveAttachment(attachment.localId) }
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Input row
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = stringResource(R.string.attachment),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.take_photo)) },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onTakePhoto()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.record_video)) },
                            leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRecordVideo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pick_from_gallery)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_file)) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }

                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.type_message),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(4.dp))

                if (canSend) {
                    IconButton(
                        onClick = onSendClick,
                        enabled = !isSending
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
