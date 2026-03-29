package northern.captain.litechat.app.ui.conversations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import northern.captain.litechat.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.navigateToChat) {
        uiState.navigateToChat?.let { id ->
            onConversationClick(id)
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState.signedOut) {
        if (uiState.signedOut) onSignOut()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onRefresh()
        onPauseOrDispose {}
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.onSignOut()
                }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 280.dp)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text(stringResource(R.string.sign_out)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSignOutDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.currentUserName.ifEmpty { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp)
                        )
                    }
                )
            }
        ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val conversations = uiState.conversations
            val users = uiState.usersWithoutConvo

            if (conversations.isEmpty() && users.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_conversations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (conversations.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.conversations))
                        }
                        items(conversations, key = { it.id }) { conversation ->
                            val otherUser = viewModel.getOtherUser(conversation)
                            ConversationItem(
                                conversation = conversation,
                                displayName = viewModel.getConversationDisplayName(conversation),
                                avatarUserId = otherUser?.userId,
                                avatarFilename = otherUser?.avatar,
                                onClick = { viewModel.onConversationClick(conversation.id) }
                            )
                        }
                    }

                    if (users.isNotEmpty()) {
                        item {
                            if (conversations.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SectionHeader(stringResource(R.string.start_chat))
                        }
                        items(users, key = { it.userId }) { user ->
                            UserItem(
                                user = user,
                                isCreating = uiState.creatingConvoForUserId == user.userId,
                                onClick = { viewModel.onUserClick(user.userId) }
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
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
