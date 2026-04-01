package northern.captain.litechat.app.ui.conversations

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
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
import northern.captain.litechat.app.data.remote.AuthManager
import northern.captain.litechat.app.ui.components.AvatarImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onTakeAvatarPhoto: () -> Unit = {},
    onCropAvatar: (filePath: String) -> Unit = {},
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val avatarGalleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onCropAvatar(uri.toString())
        }
    }

    LaunchedEffect(uiState.navigateToChat) {
        uiState.navigateToChat?.let { id ->
            onConversationClick(id)
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState.signedOut) {
        if (uiState.signedOut) onSignOut()
    }

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted or not — nothing to do */ }
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                Spacer(modifier = Modifier.height(24.dp))
                // Profile section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Box {
                        Box(modifier = Modifier.clickable { showAvatarMenu = true }) {
                            AvatarImage(
                                userId = viewModel.currentUserId,
                                name = uiState.currentUserName,
                                avatarFilename = uiState.currentUserAvatar,
                                size = 64.dp,

                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .padding(2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showAvatarMenu,
                            onDismissRequest = { showAvatarMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.take_photo)) },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                onClick = {
                                    showAvatarMenu = false
                                    scope.launch { drawerState.close() }
                                    onTakeAvatarPhoto()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pick_from_gallery)) },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    showAvatarMenu = false
                                    scope.launch { drawerState.close() }
                                    avatarGalleryPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            if (uiState.currentUserAvatar != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove_avatar)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showAvatarMenu = false
                                        viewModel.removeAvatar()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = uiState.currentUserName,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(
                                userId = viewModel.currentUserId,
                                name = uiState.currentUserName,
                                avatarFilename = uiState.currentUserAvatar,
                                size = 32.dp,

                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.currentUserName.ifEmpty { stringResource(R.string.app_name) },
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
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
