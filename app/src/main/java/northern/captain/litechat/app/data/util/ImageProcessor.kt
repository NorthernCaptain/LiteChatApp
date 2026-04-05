package northern.captain.litechat.app.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageProcessor {

    /**
     * Processes an image file for upload:
     * - Resizes to half if both dimensions stay >= 1080p (1920x1080)
     * - Re-encodes non-JPEG to JPEG 80%
     * - Applies EXIF rotation
     * - Skips JPEG files that don't need resize
     *
     * @param file The image file to process (modified in-place)
     * @param isFrontCamera Whether the image was taken with the front camera (applies horizontal flip)
     * @return The effective MIME type after processing ("image/jpeg" if processed, original otherwise)
     */
    fun processForUpload(file: File, isFrontCamera: Boolean = false): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return "application/octet-stream"

        val isJpeg = file.name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }
        val bigDim = maxOf(origW, origH)
        val smallDim = minOf(origW, origH)
        val needsResize = bigDim / 2 >= 1920 && smallDim / 2 >= 1080
        val needsReencode = !isJpeg || needsResize || isFrontCamera

        if (!needsReencode) {
            // JPEG that doesn't need resize or flip — just fix EXIF rotation
            normalizeExifRotation(file)
            return "image/jpeg"
        }

        try {
            // Decode full bitmap
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return "image/jpeg"

            // Apply EXIF rotation + front camera flip
            val rotated = applyExifRotation(file, rawBitmap, isFrontCamera)

            // Resize if needed
            val result = if (needsResize) {
                val newW = rotated.width / 2
                val newH = rotated.height / 2
                val scaled = Bitmap.createScaledBitmap(rotated, newW, newH, true)
                if (scaled !== rotated) rotated.recycle()
                scaled
            } else {
                rotated
            }

            // Write JPEG 80%
            file.outputStream().use { out ->
                result.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            result.recycle()

            // Reset EXIF orientation since we already applied it
            try {
                val exif = ExifInterface(file)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            } catch (_: Exception) {}

            return "image/jpeg"
        } catch (_: Exception) {
            // If processing fails, leave file as-is
            return if (isJpeg) "image/jpeg" else "image/${file.extension.lowercase()}"
        }
    }

    /**
     * Applies EXIF rotation and optional front-camera flip to a file in-place.
     * Called from camera capture to fix the preview before the user sees it.
     */
    fun applyRotationAndFlip(file: File, isFrontCamera: Boolean) {
        try {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f && !isFrontCamera) return

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val matrix = Matrix().apply {
                if (degrees != 0f) postRotate(degrees)
                if (isFrontCamera) postScale(-1f, 1f)
            }
            val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            file.outputStream().use { out ->
                transformed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            transformed.recycle()
            val newExif = ExifInterface(file)
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            newExif.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun normalizeExifRotation(file: File) {
        try {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return // no rotation needed
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            file.outputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            rotated.recycle()
            val newExif = ExifInterface(file)
            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            newExif.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap, isFrontCamera: Boolean): Bitmap {
        try {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f && !isFrontCamera) return bitmap

            val matrix = Matrix().apply {
                if (degrees != 0f) postRotate(degrees)
                if (isFrontCamera) postScale(-1f, 1f)
            }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (result !== bitmap) bitmap.recycle()
            return result
        } catch (_: Exception) {
            return bitmap
        }
    }
}
