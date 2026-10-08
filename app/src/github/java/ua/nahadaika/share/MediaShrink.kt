package ua.nahadaika.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Стиснення перед відправкою у спільний чат: менше трафіку й місця в безкоштовних лімітах.
 * Фото — до 1600 px JPEG (~300 КБ), відео — до 540p з бітрейтом ~1,5 Мбіт/с. Голосові вже малі — як є.
 * Оригінал на телефоні автора не змінюється.
 */
internal object MediaShrink {
    const val PHOTO_SIDE = 1600
    const val PHOTO_TARGET_BYTES = 300 * 1024
    const val VIDEO_SHORT_SIDE = 540
    const val VIDEO_BITRATE = 1_500_000

    /** Менші відео не перекодовуємо: виграш малий, а час і батарея — ні. */
    const val VIDEO_KEEP_BELOW_BYTES = 4L * 1024 * 1024

    /** Файл для відправки й його тип: стиснена копія в кеші або сам [file]. */
    suspend fun prepare(context: Context, file: File, mime: String, reminderId: String): Pair<File, String> = when {
        mime.startsWith("image/") -> {
            val out = File(context.cacheDir, "send-$reminderId.jpg")
            if (withContext(Dispatchers.Default) { photo(file, mime, out) }) out to "image/jpeg" else file to mime
        }
        mime.startsWith("video/") -> {
            val out = File(context.cacheDir, "send-$reminderId.mp4")
            if (video(context, file, out)) out to "video/mp4" else file to mime
        }
        else -> file to mime
    }

    /** false — стискати не треба (уже малий JPEG) або не вдалося: відправити оригінал. */
    fun photo(file: File, mime: String, out: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return false
        if (mime == "image/jpeg" && longSide <= PHOTO_SIDE && file.length() <= PHOTO_TARGET_BYTES) return false
        var sample = 1
        while (longSide / (sample * 2) >= PHOTO_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val scale = min(1f, PHOTO_SIDE.toFloat() / max(decoded.width, decoded.height))
        // JPEG без EXIF: поворот камери «запікаємо» в пікселі, інакше фото ляже боком.
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation(file).toFloat())
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        var quality = 85
        do {
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            quality -= 10
        } while (out.length() > PHOTO_TARGET_BYTES && quality >= 55)
        // Стиснене вийшло більшим за оригінал (буває з малими PNG) — шлемо оригінал.
        return out.length() in 1 until file.length()
    }

    private fun rotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    /** Розмір кадру так, як його показують (з урахуванням повороту). */
    private fun videoSize(file: File): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation % 180 == 0) w to h else h to w
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun video(context: Context, file: File, out: File): Boolean {
        if (file.length() < VIDEO_KEEP_BELOW_BYTES) return false
        val (w, h) = withContext(Dispatchers.IO) { videoSize(file) } ?: return false
        val scale = min(1f, VIDEO_SHORT_SIDE.toFloat() / min(w, h))
        // Кодеки хочуть парні сторони.
        val width = ((w * scale).roundToInt() / 2) * 2
        val height = ((h * scale).roundToInt() / 2) * 2
        out.delete()
        // Transformer працює на потоці з Looper — головному.
        val ok = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build()
                transformer.start(item, out.path)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
        return ok && out.length() in 1 until file.length()
    }
}
