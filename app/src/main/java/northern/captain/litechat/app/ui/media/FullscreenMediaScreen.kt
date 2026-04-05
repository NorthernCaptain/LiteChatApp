package northern.captain.litechat.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import northern.captain.litechat.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaScreen(
    onNavigateBack: () -> Unit,
    viewModel: FullscreenMediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val items = uiState.items
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.error_loading_media), color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.initialPage,
        pageCount = { items.size }
    )

    // Notify ViewModel when page changes to trigger video downloads
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageSelected(pagerState.currentPage)
    }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].attachmentId }
        ) { page ->
            val item = items[page]
            MediaPage(
                item = item,
                isCurrentPage = page == pagerState.currentPage,
                onRetry = { viewModel.retryDownload(item.attachmentId, item.filename) }
            )
        }

        // Top bar overlay
        TopAppBar(
            title = {
                val pageIndicator = if (items.size > 1) " (${pagerState.currentPage + 1}/${items.size})" else ""
                Text(
                    text = currentItem.filename + pageIndicator,
                    color = Color.White,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun MediaPage(
    item: MediaItem,
    isCurrentPage: Boolean,
    onRetry: () -> Unit
) {
    when {
        item.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.error_loading_media),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { onRetry() }) {
                        Text(stringResource(R.string.retry), color = Color.White)
                    }
                }
            }
        }
        item.isVideo -> {
            if (item.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    item.thumbnailUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val progressColor = if (item.isChunkedDownload) Color(0xFFFFD600) else Color.White
                    if (item.downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { item.downloadProgress },
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.3f)
                        )
                    } else {
                        CircularProgressIndicator(color = progressColor)
                    }
                }
            } else {
                VideoPlayer(
                    filePath = item.localFilePath ?: "",
                    isCurrentPage = isCurrentPage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        else -> {
            if (item.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    item.thumbnailUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val progressColor = if (item.isChunkedDownload) Color(0xFFFFD600) else Color.White
                    if (item.downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { item.downloadProgress },
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.3f)
                        )
                    } else {
                        CircularProgressIndicator(color = progressColor)
                    }
                }
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                AsyncImage(
                    model = java.io.File(item.localFilePath ?: ""),
                    contentDescription = item.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    if (newScale != 1f || zoom != 1f) {
                                        event.changes.forEach { it.consume() }
                                        scale = newScale
                                        if (scale > 1f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                )
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayer(
    filePath: String,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri("file://$filePath"))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
    }

    // Pause when swiped away, resume when swiped back
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerAutoShow = false
                hideController()
                post {
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = isCurrentPage
                }
            }
        },
        modifier = modifier
    )
}
