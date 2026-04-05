package northern.captain.litechat.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.core.MirrorMode
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import android.view.WindowManager
import northern.captain.litechat.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    mode: String, // "photo" or "video"
    defaultLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onResult: (file: File?, isFrontCamera: Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = mode == "video"

    val requiredPermissions = if (isVideo) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    if (!permissionsGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.camera_permission_required),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
        return
    }

    // Captured file for preview/confirmation
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var wasFrontCamera by remember { mutableStateOf(false) }

    if (capturedFile != null) {
        CapturePreviewScreen(
            file = capturedFile!!,
            isVideo = isVideo,
            onRetake = {
                capturedFile!!.delete()
                capturedFile = null
            },
            onSend = {
                onResult(capturedFile, wasFrontCamera)
            },
            onBack = {
                capturedFile!!.delete()
                onNavigateBack()
            }
        )
    } else {
        CameraViewfinder(
            isVideo = isVideo,
            defaultLensFacing = defaultLensFacing,
            onCaptured = { file, isFront ->
                capturedFile = file
                wasFrontCamera = isFront
            },
            onNavigateBack = onNavigateBack
        )
    }
}

@Composable
private fun CameraViewfinder(
    isVideo: Boolean,
    defaultLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onCaptured: (File, Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableIntStateOf(defaultLensFacing) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDurationSec++
            }
        }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(lensFacing, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            if (isVideo) {
                val qualitySelector = QualitySelector.from(
                    Quality.HD,
                    androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val vc = VideoCapture.Builder(recorder)
                    .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                    .build()
                videoCapture = vc
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, vc)
            } else {
                val rotation = (context.getSystemService(WindowManager::class.java))
                    .defaultDisplay.rotation
                val ic = ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .build()
                imageCapture = ic
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, ic)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            executor.shutdown()
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Recording indicator
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatDuration(recordingDurationSec),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isRecording) {
                        dismissed = true
                        activeRecording?.stop()
                    } else {
                        dismissed = true
                        onNavigateBack()
                    }
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            if (isRecording) {
                                activeRecording?.stop()
                            } else {
                                val vc = videoCapture ?: return@clickable
                                val file = File(
                                    context.cacheDir,
                                    "video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
                                )
                                val outputOptions = FileOutputOptions.Builder(file).build()

                                @Suppress("MissingPermission")
                                val recording = vc.output
                                    .prepareRecording(context, outputOptions)
                                    .withAudioEnabled()
                                    .start(executor) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Finalize -> {
                                                mainExecutor.execute {
                                                    isRecording = false
                                                    activeRecording = null
                                                    if (!dismissed) {
                                                        if (!event.hasError()) {
                                                            onCaptured(file, lensFacing == CameraSelector.LENS_FACING_FRONT)
                                                        } else {
                                                            file.delete()
                                                        }
                                                    } else {
                                                        if (!event.hasError()) {
                                                            onCaptured(file, lensFacing == CameraSelector.LENS_FACING_FRONT)
                                                        } else {
                                                            file.delete()
                                                            onNavigateBack()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                activeRecording = recording
                                isRecording = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            val ic = imageCapture ?: return@clickable
                            isCapturing = true
                            val file = File(
                                context.cacheDir,
                                "photo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                            )
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                            ic.takePicture(
                                outputOptions,
                                executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
                                        // Apply rotation + flip so preview looks correct
                                        northern.captain.litechat.app.data.util.ImageProcessor.applyRotationAndFlip(file, isFront)
                                        mainExecutor.execute { onCaptured(file, isFront) }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        mainExecutor.execute { isCapturing = false }
                                        file.delete()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (!isRecording) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.switch_camera),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun CapturePreviewScreen(
    file: File,
    isVideo: Boolean,
    onRetake: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isVideo) {
            VideoPreviewPlayer(
                filePath = file.absolutePath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Bottom Retake / Send buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.retake))
            }

            Button(
                onClick = onSend,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.send_attachment))
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPreviewPlayer(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("file://$filePath"))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
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
                    exoPlayer.playWhenReady = true
                }
            }
        },
        modifier = modifier
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
