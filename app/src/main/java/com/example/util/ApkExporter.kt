package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

data class ApkMetadata(
    val versionName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val suggestedFileName: String,
    val isAvailable: Boolean,
)

object ApkExporter {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun getApkMetadata(context: Context): ApkMetadata {
        val versionName = BuildConfig.VERSION_NAME
        val sourcePath = context.applicationInfo?.sourceDir
        val apkFile = sourcePath?.let(::File)?.takeIf { it.exists() && it.canRead() }
        val sizeBytes = apkFile?.length() ?: 0L
        val sanitizedVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "MyDailyRoutine-v$sanitizedVersion.apk"
        return ApkMetadata(
            versionName = versionName,
            sizeBytes = sizeBytes,
            formattedSize = formatByteSize(sizeBytes),
            suggestedFileName = fileName,
            isAvailable = apkFile != null && sizeBytes > 0L
        )
    }

    suspend fun saveApkToDownloads(context: Context, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            val sourceFile = getReadableSourceApk(context)
                ?: return@withContext Result.failure(IllegalStateException("Installed APK file is unavailable."))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext Result.failure(UnsupportedOperationException("Use document picker"))
            }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IOException("Unable to create file in Downloads."))

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 65_536)
                    }
                } ?: throw IOException("Unable to open destination stream.")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
                Result.success("Saved $fileName to Downloads")
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                Result.failure(t)
            }
        }

    suspend fun exportApkToUri(context: Context, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            val sourceFile = getReadableSourceApk(context)
                ?: return@withContext Result.failure(IllegalStateException("Installed APK file is unavailable."))

            runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 65_536)
                    }
                } ?: throw IOException("Could not open selected destination.")
                Unit
            }
        }

    suspend fun prepareShareApkIntent(context: Context, fileName: String): Result<Intent> =
        withContext(Dispatchers.IO) {
            val sourceFile = getReadableSourceApk(context)
                ?: return@withContext Result.failure(IllegalStateException("Installed APK file is unavailable."))

            runCatching {
                val shareDir = File(context.cacheDir, "apk").apply { mkdirs() }
                shareDir.listFiles()?.forEach { oldFile ->
                    if (oldFile.name != fileName) {
                        oldFile.delete()
                    }
                }
                val cachedApk = File(shareDir, fileName)
                sourceFile.inputStream().use { input ->
                    cachedApk.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 65_536)
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cachedApk
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = APK_MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "My Daily Routine v${BuildConfig.VERSION_NAME} APK")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                Intent.createChooser(sendIntent, "Share My Daily Routine APK")
            }
        }

    private fun getReadableSourceApk(context: Context): File? {
        val path = context.applicationInfo?.sourceDir ?: return null
        return File(path).takeIf { it.exists() && it.canRead() }
    }

    internal fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = (bytes / 1024.0).coerceAtLeast(1.0)
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
