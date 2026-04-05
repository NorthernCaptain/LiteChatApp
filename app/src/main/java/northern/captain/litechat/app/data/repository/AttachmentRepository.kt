package northern.captain.litechat.app.data.repository

import android.content.Context
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.domain.model.Attachment
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    private val api: LiteChatApi,
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "attachments").also { it.mkdirs() }

    private val maxCacheSize = 500L * 1024 * 1024 // 500MB

    suspend fun uploadAttachment(file: File, mimeType: String): Attachment {
        return uploadAttachment(file, mimeType, null)
    }

    suspend fun uploadAttachment(file: File, mimeType: String, onProgress: ((Float) -> Unit)?): Attachment {
        val mediaType = mimeType.toMediaType()
        val requestBody = if (onProgress != null) {
            ProgressRequestBody(file, mediaType, onProgress)
        } else {
            file.asRequestBody(mediaType)
        }
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val response = api.uploadAttachment(part)
        return Attachment(
            id = response.id,
            originalFilename = response.originalFilename,
            mimeType = response.mimeType,
            size = response.size,
            hasThumbnail = response.hasThumbnail
        )
    }

    private class ProgressRequestBody(
        private val file: File,
        private val mediaType: MediaType,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength() = file.length()

        override fun writeTo(sink: BufferedSink) {
            val totalBytes = file.length()
            var uploaded = 0L
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress((uploaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                }
            }
        }
    }

    suspend fun downloadOriginal(attachmentId: String, filename: String): File {
        return downloadOriginal(attachmentId, filename, 0, null)
    }

    companion object {
        private const val CHUNK_SIZE = 5L * 1024 * 1024 // 5MB chunks
        private const val MAX_RETRIES = 5
    }

    suspend fun downloadOriginal(
        attachmentId: String,
        filename: String,
        knownSize: Long,
        onProgress: ((Float) -> Unit)?,
        onChunkedMode: ((Boolean) -> Unit)? = null
    ): File {
        val targetFile = File(cacheDir, "${attachmentId}_$filename")
        if (targetFile.exists()) return targetFile

        val tempFile = File(cacheDir, "${attachmentId}_$filename.tmp")
        val totalBytes = knownSize
        val isChunked = totalBytes > 0 && totalBytes > CHUNK_SIZE
        onChunkedMode?.invoke(isChunked)

        if (isChunked) {
            downloadChunked(attachmentId, tempFile, totalBytes, onProgress)
        } else {
            downloadSingle(attachmentId, tempFile, totalBytes, onProgress)
        }

        tempFile.renameTo(targetFile)
        evictIfNeeded()
        return targetFile
    }

    private suspend fun downloadSingle(
        attachmentId: String,
        tempFile: File,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?
    ) {
        try {
            val response = api.downloadAttachment(attachmentId)
            tempFile.outputStream().use { output ->
                response.byteStream().use { input ->
                    if (onProgress != null && totalBytes > 0) {
                        copyWithProgress(input, output, totalBytes, 0L, onProgress)
                    } else {
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private suspend fun downloadChunked(
        attachmentId: String,
        tempFile: File,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?
    ) {
        // Resume from existing temp file if present
        var downloaded = if (tempFile.exists()) tempFile.length() else 0L

        while (downloaded < totalBytes) {
            val end = (downloaded + CHUNK_SIZE - 1).coerceAtMost(totalBytes - 1)
            var retries = 0
            var chunkSuccess = false

            while (retries < MAX_RETRIES && !chunkSuccess) {
                try {
                    val response = api.downloadAttachmentRange(
                        attachmentId,
                        "bytes=$downloaded-$end"
                    )
                    java.io.RandomAccessFile(tempFile, "rw").use { raf ->
                        raf.seek(downloaded)
                        response.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                raf.write(buffer, 0, read)
                                downloaded += read
                                onProgress?.invoke((downloaded.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                    chunkSuccess = true
                } catch (_: Exception) {
                    retries++
                    if (retries >= MAX_RETRIES) {
                        tempFile.delete()
                        throw java.io.IOException("Download failed after $MAX_RETRIES retries at $downloaded/$totalBytes bytes")
                    }
                    // Brief pause before retry
                    kotlinx.coroutines.delay(1000L * retries)
                    // Re-read actual file size in case partial write happened
                    downloaded = if (tempFile.exists()) tempFile.length() else 0L
                }
            }
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        startOffset: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = startOffset
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            onProgress((totalRead.toFloat() / totalBytes).coerceIn(0f, 1f))
        }
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles() ?: return

        // Clean up stale .tmp files older than 3 days
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        files.filter { it.name.endsWith(".tmp") && it.lastModified() < threeDaysAgo }
            .forEach { it.delete() }

        // LRU eviction if over size limit
        val remaining = cacheDir.listFiles() ?: return
        val totalSize = remaining.sumOf { it.length() }
        if (totalSize <= maxCacheSize) return

        val sorted = remaining.sortedBy { it.lastModified() }
        var freed = 0L
        val target = totalSize - maxCacheSize
        for (file in sorted) {
            freed += file.length()
            file.delete()
            if (freed >= target) break
        }
    }
}
