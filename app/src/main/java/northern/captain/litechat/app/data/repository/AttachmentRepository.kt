package northern.captain.litechat.app.data.repository

import android.content.Context
import northern.captain.litechat.app.data.remote.LiteChatApi
import northern.captain.litechat.app.domain.model.Attachment
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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
        val requestBody = file.asRequestBody(mimeType.toMediaType())
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

    suspend fun downloadOriginal(attachmentId: String, filename: String): File {
        val targetFile = File(cacheDir, "${attachmentId}_$filename")
        if (targetFile.exists()) return targetFile

        val response = api.downloadAttachment(attachmentId)
        targetFile.outputStream().use { output ->
            response.byteStream().use { input ->
                input.copyTo(output)
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
