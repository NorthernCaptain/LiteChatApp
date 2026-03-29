package northern.captain.litechat.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import northern.captain.litechat.app.R
import northern.captain.litechat.app.domain.model.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onMediaClick: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showReactionPicker by remember { mutableStateOf<Message?>(null) }
    var longPressOffset by remember { mutableStateOf(Offset.Zero) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToBottom.collect {
            listState.animateScrollToItem(0)
        }
    }

    // Sliding window: load older when near top, load newer when near bottom
    val nearTop by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    val nearBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 2
        }
    }
    LaunchedEffect(nearTop) {
        if (nearTop) viewModel.loadOlderMessages()
    }
    LaunchedEffect(nearBottom) {
        if (nearBottom) viewModel.loadNewerMessages()
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.conversationName,
                        style = MaterialTheme.typography.titleLarge
                    )
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
            ChatInput(
                inputText = uiState.inputText,
                replyToMessage = uiState.replyToMessage,
                pendingAttachments = uiState.pendingAttachments,
                isSending = uiState.isSending,
                onInputChange = viewModel::onInputChange,
                onSendClick = viewModel::onSendClick,
                onAttachmentPicked = viewModel::onAttachmentPicked,
                onRemoveAttachment = viewModel::onRemovePendingAttachment,
                onCancelReply = viewModel::onCancelReply,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
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

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                        onMediaClick = onMediaClick
                    )
                }

            }
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
