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

    suspend fun downloadOriginal(
        attachmentId: String,
        filename: String,
        knownSize: Long,
        onProgress: ((Float) -> Unit)?
    ): File {
        val targetFile = File(cacheDir, "${attachmentId}_$filename")
        if (targetFile.exists()) return targetFile

        val response = api.downloadAttachment(attachmentId)
        val contentLength = response.contentLength()
        val totalBytes = if (contentLength > 0) contentLength else knownSize
        targetFile.outputStream().use { output ->
            response.byteStream().use { input ->
                if (onProgress != null && totalBytes > 0) {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress((totalRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                } else {
                    input.copyTo(output)
                }
            }
        }
        evictIfNeeded()
        return targetFile
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        if (totalSize <= maxCacheSize) return

        val sorted = files.sortedBy { it.lastModified() }
        var freed = 0L
        val target = totalSize - maxCacheSize
        for (file in sorted) {
            freed += file.length()
            file.delete()
            if (freed >= target) break
        }
    }
}
