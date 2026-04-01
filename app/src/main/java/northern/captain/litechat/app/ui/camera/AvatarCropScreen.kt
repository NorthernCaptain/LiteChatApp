package northern.captain.litechat.app.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.net.Uri
import northern.captain.litechat.app.R
import java.io.File

@Composable
fun AvatarCropScreen(
    filePath: String,
    onResult: (File?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var isCropping by remember { mutableStateOf(false) }

    // Crop square position and size (in view coordinates)
    var cropSize by remember { mutableFloatStateOf(0f) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var initialized by remember { mutableStateOf(false) }
    var dragCorner by remember { mutableIntStateOf(0) } // 0=move, 1=TL, 2=TR, 3=BL, 4=BR

    // Initialize crop square to center when view is measured
    LaunchedEffect(viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0 && !initialized) {
            val side = (minOf(viewSize.width, viewSize.height) * 0.75f)
            cropSize = side
            cropOffset = Offset(
                (viewSize.width - side) / 2f,
                (viewSize.height - side) / 2f
            )
            initialized = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Image
        AsyncImage(
            model = filePath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    viewSize = IntSize(coords.size.width, coords.size.height)
                }
        )

        // Crop overlay
        if (initialized) {
            val cornerHitSize = 48f
            val minCropSize = 100f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startPos ->
                                // Determine drag mode: corner resize or move
                                val right = cropOffset.x + cropSize
                                val bottom = cropOffset.y + cropSize
                                dragCorner = when {
                                    // Bottom-right corner
                                    startPos.x > right - cornerHitSize && startPos.y > bottom - cornerHitSize -> 4
                                    // Bottom-left corner
                                    startPos.x < cropOffset.x + cornerHitSize && startPos.y > bottom - cornerHitSize -> 3
                                    // Top-right corner
                                    startPos.x > right - cornerHitSize && startPos.y < cropOffset.y + cornerHitSize -> 2
                                    // Top-left corner
                                    startPos.x < cropOffset.x + cornerHitSize && startPos.y < cropOffset.y + cornerHitSize -> 1
                                    // Inside crop area — move
                                    else -> 0
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxW = viewSize.width.toFloat()
                                val maxH = viewSize.height.toFloat()
                                when (dragCorner) {
                                    0 -> {
                                        // Move
                                        cropOffset = Offset(
                                            (cropOffset.x + dragAmount.x).coerceIn(0f, maxW - cropSize),
                                            (cropOffset.y + dragAmount.y).coerceIn(0f, maxH - cropSize)
                                        )
                                    }
                                    4 -> {
                                        // Resize from bottom-right
                                        val delta = maxOf(dragAmount.x, dragAmount.y)
                                        val newSize = (cropSize + delta)
                                            .coerceIn(minCropSize, minOf(maxW - cropOffset.x, maxH - cropOffset.y))
                                        cropSize = newSize
                                    }
                                    1 -> {
                                        // Resize from top-left
                                        val delta = maxOf(-dragAmount.x, -dragAmount.y)
                                        val newSize = (cropSize + delta).coerceIn(minCropSize, cropSize + minOf(cropOffset.x, cropOffset.y))
                                        val sizeDiff = newSize - cropSize
                                        cropOffset = Offset(cropOffset.x - sizeDiff, cropOffset.y - sizeDiff)
                                        cropSize = newSize
                                    }
                                    2 -> {
                                        // Resize from top-right
                                        val delta = maxOf(dragAmount.x, -dragAmount.y)
                                        val newSize = (cropSize + delta)
                                            .coerceIn(minCropSize, minOf(maxW - cropOffset.x, cropSize + cropOffset.y))
                                        val sizeDiff = newSize - cropSize
                                        cropOffset = Offset(cropOffset.x, cropOffset.y - sizeDiff)
                                        cropSize = newSize
                                    }
                                    3 -> {
                                        // Resize from bottom-left
                                        val delta = maxOf(-dragAmount.x, dragAmount.y)
                                        val newSize = (cropSize + delta)
                                            .coerceIn(minCropSize, minOf(cropSize + cropOffset.x, maxH - cropOffset.y))
                                        val sizeDiff = newSize - cropSize
                                        cropOffset = Offset(cropOffset.x - sizeDiff, cropOffset.y)
                                        cropSize = newSize
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Dim area outside crop
                val cropRect = Rect(cropOffset, Size(cropSize, cropSize))

                // Top
                drawRect(Color.Black.copy(alpha = 0.6f), Offset.Zero, Size(size.width, cropRect.top))
                // Bottom
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(0f, cropRect.bottom), Size(size.width, size.height - cropRect.bottom))
                // Left
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(0f, cropRect.top), Size(cropRect.left, cropSize))
                // Right
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(cropRect.right, cropRect.top), Size(size.width - cropRect.right, cropSize))

                // Crop border
                drawRect(Color.White, cropRect.topLeft, cropRect.size, style = Stroke(width = 2.dp.toPx()))

                // Corner handles
                val handleSize = 16.dp.toPx()
                val handleStroke = 3.dp.toPx()
                listOf(
                    cropRect.topLeft,
                    Offset(cropRect.right - handleSize, cropRect.top),
                    Offset(cropRect.left, cropRect.bottom - handleSize),
                    Offset(cropRect.right - handleSize, cropRect.bottom - handleSize)
                ).forEach { corner ->
                    drawRect(Color.White, corner, Size(handleSize, handleSize), style = Stroke(width = handleStroke))
                }
            }
        }

        // Top back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White
            )
        }

        // Bottom buttons
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
                onClick = onNavigateBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(stringResource(R.string.cancel))
            }

            Button(
                onClick = {
                    if (!isCropping) {
                        isCropping = true
                        scope.launch(Dispatchers.IO) {
                            val croppedFile = cropAndSave(
                                context,
                                filePath,
                                viewSize,
                                cropOffset,
                                cropSize
                            )
                            withContext(Dispatchers.Main) { onResult(croppedFile) }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

private fun cropAndSave(
    context: android.content.Context,
    filePath: String,
    viewSize: IntSize,
    cropOffset: Offset,
    cropSize: Float
): File? {
    try {
        val rawBitmap = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
            val uri = Uri.parse(filePath)
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } else {
            BitmapFactory.decodeFile(filePath)
        } ?: return null

        // Apply EXIF rotation
        val bitmap = applyExifRotation(context, filePath, rawBitmap)
        val imgW = bitmap.width
        val imgH = bitmap.height

        // Calculate how the image fits in the view (ContentScale.Fit)
        val viewW = viewSize.width.toFloat()
        val viewH = viewSize.height.toFloat()
        val scale = minOf(viewW / imgW, viewH / imgH)
        val displayW = imgW * scale
        val displayH = imgH * scale
        val offsetX = (viewW - displayW) / 2f
        val offsetY = (viewH - displayH) / 2f

        // Map crop rect from view coordinates to image coordinates
        val imgCropX = ((cropOffset.x - offsetX) / scale).coerceIn(0f, imgW.toFloat())
        val imgCropY = ((cropOffset.y - offsetY) / scale).coerceIn(0f, imgH.toFloat())
        val imgCropSize = (cropSize / scale).coerceAtMost(
            minOf(imgW - imgCropX, imgH - imgCropY)
        )

        if (imgCropSize <= 0) return null

        val cropped = Bitmap.createBitmap(
            bitmap,
            imgCropX.toInt(),
            imgCropY.toInt(),
            imgCropSize.toInt(),
            imgCropSize.toInt()
        )
        bitmap.recycle()

        // Scale to 256x256
        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
        cropped.recycle()

        val outFile = File(context.cacheDir, "avatar_crop.jpg")
        outFile.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        scaled.recycle()
        return outFile
    } catch (_: Exception) {
        return null
    }
}

private fun applyExifRotation(context: android.content.Context, filePath: String, bitmap: Bitmap): Bitmap {
    try {
        val exif = if (filePath.startsWith("content://")) {
            val uri = Uri.parse(filePath)
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } else {
            ExifInterface(filePath)
        } ?: return bitmap

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotated
    } catch (_: Exception) {
        return bitmap
    }
}
