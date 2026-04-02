package northern.captain.litechat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import northern.captain.litechat.app.R
import northern.captain.litechat.app.ui.components.AvatarImage
import northern.captain.litechat.app.domain.model.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onMediaClick: (attachmentId: String, messageId: String) -> Unit,
    onTakePhoto: () -> Unit = {},
    onRecordVideo: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showReactionPicker by remember { mutableStateOf<Message?>(null) }
    var longPressOffset by remember { mutableStateOf(Offset.Zero) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show upload error
    val uploadError = uiState.uploadError
    val uploadFailedMsg = stringResource(R.string.upload_failed, uploadError ?: "")
    val fileTooLargeMsg = stringResource(R.string.file_too_large)
    LaunchedEffect(uploadError) {
        if (uploadError != null) {
            val msg = if (uploadError == "too_large") fileTooLargeMsg else uploadFailedMsg
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUploadError()
        }
    }

    // Show send error
    val sendError = uiState.sendError
    val sendFailedMsg = stringResource(R.string.send_failed)
    LaunchedEffect(sendError) {
        if (sendError) {
            snackbarHostState.showSnackbar(sendFailedMsg)
            viewModel.clearSendError()
        }
    }

    // When messages change and user is near bottom, keep them at bottom
    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId != null && listState.firstVisibleItemIndex <= 3) {
            listState.scrollToItem(0)
        }
    }

    // Force scroll to bottom after sending
    LaunchedEffect(Unit) {
        viewModel.scrollToBottom.collect {
            // Small delay to ensure Room has emitted the new message
            kotlinx.coroutines.delay(200)
            listState.scrollToItem(0)
        }
    }

    // Sliding window: load older when near top, load newer when near bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            val nearTop = total > 0 && lastVisible >= total - 3
            val nearBottom = listState.firstVisibleItemIndex <= 2
            nearTop to nearBottom
        }.collect { (nearTop, nearBottom) ->
            if (nearTop) viewModel.loadOlderMessages()
            if (nearBottom) viewModel.loadNewerMessages()
        }
    }

    // Reaction picker popup
    showReactionPicker?.let { message ->
        ReactionPicker(
            touchOffset = longPressOffset,
            onEmojiSelected = { emoji ->
                viewModel.onToggleReaction(message.id, emoji)
            },
            onDismiss = { showReactionPicker = null }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(
                            userId = uiState.avatarUserId,
                            name = uiState.conversationName,
                            avatarFilename = uiState.avatarFilename,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.conversationName,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Typing indicator
                TypingIndicator(typingUsers = uiState.typingUsers)

                ChatInput(
                    inputText = uiState.inputText,
                    replyToMessage = uiState.replyToMessage,
                    pendingAttachments = uiState.pendingAttachments,
                    isSending = uiState.isSending,
                    onInputChange = viewModel::onInputChange,
                    onSendClick = viewModel::onSendClick,
                    onAttachmentsPicked = viewModel::onAttachmentsPicked,
                    onRemoveAttachment = viewModel::onRemovePendingAttachment,
                    onCancelReply = viewModel::onCancelReply,
                    onTakePhoto = onTakePhoto,
                    onRecordVideo = onRecordVideo
                )
            }
        }
    ) { padding ->
        if (uiState.isInitialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_messages),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val messages = uiState.messages.reversed() // newest first for reverseLayout
            val showScrollToBottom by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 5 }
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val index = messages.indexOf(message)
                    val previousMessage = messages.getOrNull(index + 1) // previous in time (next in reversed list)
                    val isFirstInGroup = previousMessage == null ||
                        previousMessage.senderId != message.senderId ||
                        isTimeDifferenceSignificant(previousMessage.createdAt, message.createdAt)

                    MessageBubble(
                        message = message,
                        isOwnMessage = message.senderId == uiState.currentUserId,
                        isFirstInGroup = isFirstInGroup,
                        isGroupConvo = uiState.conversationType == "group",
                        currentUserId = uiState.currentUserId,
                        onReactionClick = { msgId, emoji ->
                            viewModel.onToggleReaction(msgId, emoji)
                        },
                        onLongPress = { msg, offset ->
                            longPressOffset = offset
                            showReactionPicker = msg
                        },
                        onMediaClick = onMediaClick,
                        onFileClick = viewModel::onOpenFile,
                        downloadingAttachmentId = uiState.downloadingAttachmentId,
                        downloadProgress = uiState.downloadProgress
                    )
                }
            }

            // Scroll-to-bottom button
            if (showScrollToBottom) {
                SmallFloatingActionButton(
                    onClick = { viewModel.jumpToLatest() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 2.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }
            } // Box
        }
    }
}

@Composable
private fun TypingIndicator(typingUsers: Map<Long, String>) {
    AnimatedVisibility(visible = typingUsers.isNotEmpty()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = typingUsers.values.joinToString(", ") + " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WaveDots()
        }
    }
}

@Composable
private fun WaveDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 800
                        0f at 0
                        -6f at 200
                        0f at 400
                        0f at 800
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .offset(y = offsetY.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

private fun isTimeDifferenceSignificant(time1: String, time2: String): Boolean {
    return try {
        val t1 = java.time.Instant.parse(time1)
        val t2 = java.time.Instant.parse(time2)
        java.time.Duration.between(t1, t2).abs().toMinutes() > 2
    } catch (_: Exception) {
        true
    }
}
