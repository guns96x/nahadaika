package ua.nahadaika.share

import android.content.Context
import ua.nahadaika.data.Kind
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Безпечна робота з медіафайлами спільних чатів: перевірка шляхів, обмеження розміру та атомарне збереження.
 */
object MediaSync {
    const val MAX_MEDIA_SIZE_BYTES = 25L * 1024 * 1024 // 25 МБ

    val ALLOWED_EXTENSIONS = setOf("m4a", "mp4", "jpg", "jpeg", "png", "webp")

    private val SAFE_MEDIA_REF_REGEX = Regex("""^chats/[a-zA-Z0-9_-]+/media/[a-zA-Z0-9_.-]+$""")

    fun isSafeMediaRef(mediaRef: String?): Boolean {
        if (mediaRef.isNullOrBlank()) return false
        if (mediaRef.contains("..") || mediaRef.contains('\\') || mediaRef.startsWith("/")) return false
        if (!SAFE_MEDIA_REF_REGEX.matches(mediaRef)) return false
        val ext = mediaRef.substringAfterLast('.', "").lowercase()
        return ext in ALLOWED_EXTENSIONS
    }

    fun storagePath(chatId: String, reminderId: String, ext: String): String {
        val safeExt = ext.lowercase().takeIf { it in ALLOWED_EXTENSIONS } ?: "bin"
        return "chats/$chatId/media/$reminderId.$safeExt"
    }

    fun mediaDir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun isLocalMedia(context: Context, file: File): Boolean =
        file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)

    fun localTargetFile(context: Context, reminderId: String, ext: String): File {
        val safeExt = ext.lowercase().takeIf { it in ALLOWED_EXTENSIONS } ?: "bin"
        val dir = mediaDir(context)
        val file = File(dir, "shared_${reminderId}.$safeExt")
        check(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Небезпечний локальний шлях файлу" }
        return file
    }

    fun localTempFile(context: Context, reminderId: String, ext: String): File {
        val safeExt = ext.lowercase().takeIf { it in ALLOWED_EXTENSIONS } ?: "bin"
        val dir = mediaDir(context)
        val file = File(dir, "shared_${reminderId}.$safeExt.tmp_${UUID.randomUUID()}")
        check(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Небезпечний локальний шлях файлу" }
        return file
    }

    fun atomicPromote(tempFile: File, targetFile: File): Boolean = try {
        if (!tempFile.exists() || tempFile.length() == 0L || tempFile.length() > MAX_MEDIA_SIZE_BYTES) {
            tempFile.delete()
            false
        } else {
            targetFile.parentFile?.mkdirs()
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        }
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        // Якщо файлова система не підтримує ATOMIC_MOVE, виконуємо звичайну заміну
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    } catch (_: Exception) {
        tempFile.delete()
        false
    }

    fun mimeFor(kind: Kind, ext: String): String = when (kind) {
        Kind.VOICE -> "audio/mp4"
        Kind.VIDEO -> "video/mp4"
        Kind.PHOTO -> when (ext.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        Kind.TEXT -> "text/plain"
    }
}
