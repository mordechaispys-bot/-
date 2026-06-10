package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class VaultRepository(private val context: Context, private val dao: VaultDao) {

    val breakInLogs: Flow<List<BreakInLog>> = dao.getBreakInLogs()

    fun getVaultItems(isDecoy: Boolean): Flow<List<VaultItem>> {
        return dao.getVaultItems(isDecoy)
    }

    suspend fun insertItem(item: VaultItem) {
        dao.insertVaultItem(item)
    }

    suspend fun deleteItem(item: VaultItem) {
        withContext(Dispatchers.IO) {
            // Delete actual internal file
            val file = File(context.filesDir, item.internalPath)
            if (file.exists()) {
                file.delete()
            }
            dao.deleteVaultItem(item)
        }
    }

    // Settings helpers
    suspend fun getPin(): String? = dao.getSetting("PIN")?.value
    suspend fun setPin(pin: String) = dao.insertSetting(VaultSetting("PIN", pin))

    suspend fun getDecoyPin(): String? = dao.getSetting("DECOY_PIN")?.value
    suspend fun setDecoyPin(pin: String) = dao.insertSetting(VaultSetting("DECOY_PIN", pin))

    suspend fun isStealthMode(): Boolean {
        val setting = dao.getSetting("STEALTH_MODE")
        return setting?.value?.toBoolean() ?: true // Default to true (calculator mode active)
    }

    suspend fun setStealthMode(enabled: Boolean) {
        dao.insertSetting(VaultSetting("STEALTH_MODE", enabled.toString()))
    }

    // Break-In Attempts Tracker
    suspend fun logBreakInAttempt(enteredCode: String) {
        withContext(Dispatchers.IO) {
            val log = BreakInLog(
                enteredPin = enteredCode,
                timestamp = System.currentTimeMillis()
            )
            dao.insertBreakInLog(log)
        }
    }

    suspend fun clearBreakInLogs() {
        dao.clearBreakInLogs()
    }

    /**
     * Imports a public media file (URI) to the secure private app storage.
     * Returns the relative path inside context.filesDir, or null if fails.
     */
    suspend fun importMedia(uri: Uri, isDecoy: Boolean): VaultItem? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(contentResolver, uri) ?: "media_${UUID.randomUUID()}"
            val mimeType = contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video") || fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi")
            val fileType = if (isVideo) "VIDEO" else "IMAGE"

            // Get standard file extension
            val extension = getFileExtension(fileName, mimeType)
            val secureDirectory = File(context.filesDir, "vault_secure_media")
            if (!secureDirectory.exists()) {
                secureDirectory.mkdirs()
            }

            // Private name inside files
            val secureFileName = "enc_${UUID.randomUUID()}$extension"
            val targetFile = File(secureDirectory, secureFileName)

            var size = 0L
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        size += bytesRead
                    }
                }
            }

            if (targetFile.exists() && size > 0) {
                val relativePath = "vault_secure_media/$secureFileName"
                val item = VaultItem(
                    fileName = fileName,
                    fileType = fileType,
                    internalPath = relativePath,
                    fileSize = size,
                    isDecoy = isDecoy
                )
                dao.insertVaultItem(item)
                item
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error importing media: ${e.message}", e)
            null
        }
    }

    /**
     * Imports a Bitmap or captured photo stream directly.
     * Safe for direct-to-vault camera triggers.
     */
    suspend fun importCapturedBytes(bytes: ByteArray, fileName: String, isDecoy: Boolean): VaultItem? = withContext(Dispatchers.IO) {
        try {
            val secureDirectory = File(context.filesDir, "vault_secure_media")
            if (!secureDirectory.exists()) {
                secureDirectory.mkdirs()
            }

            val secureFileName = "enc_${UUID.randomUUID()}.jpg"
            val targetFile = File(secureDirectory, secureFileName)

            FileOutputStream(targetFile).use { outputStream ->
                outputStream.write(bytes)
            }

            if (targetFile.exists()) {
                val relativePath = "vault_secure_media/$secureFileName"
                val item = VaultItem(
                    fileName = fileName,
                    fileType = "IMAGE",
                    internalPath = relativePath,
                    fileSize = bytes.size.toLong(),
                    isDecoy = isDecoy
                )
                dao.insertVaultItem(item)
                item
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Error saving direct capture: ${e.message}", e)
            null
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getFileExtension(fileName: String, mimeType: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex >= 0) {
            return fileName.substring(dotIndex)
        }
        return when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
            mimeType.contains("png") -> ".png"
            mimeType.contains("gif") -> ".gif"
            mimeType.contains("mp4") -> ".mp4"
            mimeType.contains("quicktime") -> ".mov"
            else -> ".bin"
        }
    }
}
