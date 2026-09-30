package ua.nahadaika.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import ua.nahadaika.data.Kind
import java.io.File
import java.util.UUID

/** Вкладення, ще не відправлене в розклад. */
data class Attachment(val kind: Kind, val file: File, val durationMs: Long = 0)

object MediaFiles {
    private fun dir(context: Context) = File(context.filesDir, "media").apply { mkdirs() }

    fun newFile(context: Context, ext: String) = File(dir(context), "${UUID.randomUUID()}.$ext")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    /** Копіює файл з галереї у власне сховище, щоб він не зник до моменту нагадування. */
    fun importFromUri(context: Context, uri: Uri): Attachment? {
        val mime = context.contentResolver.getType(uri) ?: return null
        val isVideo = mime.startsWith("video/")
        val ext = when {
            isVideo -> "mp4"
            mime.contains("png") -> "png"
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val file = newFile(context, ext)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return null
            if (isVideo) Attachment(Kind.VIDEO, file, durationOf(file)) else Attachment(Kind.PHOTO, file)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }
}

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start(): Boolean {
        val f = MediaFiles.newFile(context, "m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            true
        } catch (_: Exception) {
            r.release()
            f.delete()
            false
        }
    }

    fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (_: Exception) {
        0
    }

    /** Зупиняє запис і повертає вкладення (або null, якщо запис занадто короткий/зіпсований). */
    fun stop(): Attachment? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val ok = try {
            r.stop()
            true
        } catch (_: Exception) {
            false
        } finally {
            r.release()
        }
        if (f == null) return null
        if (!ok) {
            f.delete()
            return null
        }
        return Attachment(Kind.VOICE, f, MediaFiles.durationOf(f))
    }

    fun cancel() {
        stop()?.file?.delete()
    }
}

/** Програвач голосових: один трек одночасно, як у месенджерах. */
class AudioPlayer {
    var currentPath by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var player: MediaPlayer? = null

    fun toggle(path: String) {
        val p = player
        if (path == currentPath && p != null) {
            if (p.isPlaying) p.pause() else p.start()
            isPlaying = p.isPlaying
            return
        }
        play(path)
    }

    fun play(path: String) {
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            currentPath = path
            isPlaying = true
        } catch (_: Exception) {
            stop()
        }
    }

    fun seek(fraction: Float) {
        player?.let { it.seekTo((it.duration * fraction).toInt()); progress = fraction }
    }

    /** Викликається з UI кожні ~100 мс. */
    fun tick() {
        val p = player ?: return
        if (p.duration > 0) progress = p.currentPosition.toFloat() / p.duration
    }

    fun stop() {
        player?.release()
        player = null
        currentPath = null
        isPlaying = false
        progress = 0f
    }
}
