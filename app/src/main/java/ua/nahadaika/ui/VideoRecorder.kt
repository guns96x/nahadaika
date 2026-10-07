package ua.nahadaika.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ua.nahadaika.R
import ua.nahadaika.Res
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.data.Kind
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.MediaFiles
import java.io.File

private const val TAG = "VideoCircle"

/** Один запис: файл і рішення, чи зберегти його після зупинки. */
private class Take(val file: File) {
    var keep = false
    var durationMs = 0L
    var recording: Recording? = null
}

/** Що сказати людині, якщо камера не записала відео. */
private fun finalizeMessage(error: Int): String = when (error) {
    VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> Res.s(R.string.chat_video_error_storage)
    VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> Res.s(R.string.chat_video_error_encoding)
    VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> Res.s(R.string.chat_video_error_no_data)
    else -> Res.s(R.string.chat_video_error_generic, error)
}

/**
 * Відео-«кружечок», як у Telegram: камера відкривається й запис стартує одразу.
 * [finish]: null — іде запис; true — зберегти; false — відкинути. Результат — у [onResult].
 * [front] змінюється кнопкою ⟲ (лише в режимі 🔒): запис не переривається — той самий файл
 * продовжується з іншої камери (persistent recording), як у Telegram.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalPersistentRecording::class])
@SuppressLint("MissingPermission") // дозволи перевіряються перед показом, на мікрофон — нижче
@Composable
fun VideoCircleRecorder(
    front: Boolean,
    finish: Boolean?,
    locked: Boolean,
    onFlip: () -> Unit,
    onElapsed: (Long) -> Unit,
    onResult: (Attachment?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnElapsed by rememberUpdatedState(onElapsed)
    val currentOnFlip by rememberUpdatedState(onFlip)
    var take by remember { mutableStateOf<Take?>(null) }
    // Поки камера перемикається, кнопку ⟲ ховаємо — подвійне натискання ламало прив'язку.
    var switching by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView — щоб Compose міг обрізати превʼю по колу.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        VideoCapture.Builder(recorder).setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build()
    }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    fun startTake() {
        val t = Take(MediaFiles.newFile(context, "mp4"))
        val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(t.file).build())
            .asPersistentRecording()
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        currentOnElapsed(0)
        take = t
        t.recording = (if (audioGranted) pending.withAudioEnabled() else pending).start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    t.durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                    if (take === t) currentOnElapsed(t.durationMs)
                }
                is VideoRecordEvent.Finalize -> {
                    val errorOk = event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
                    if (!errorOk) Log.w(TAG, "Finalize error ${event.error}", event.cause)
                    val ok = t.keep && t.file.length() > 0 && t.durationMs >= 700 && errorOk
                    when {
                        ok -> currentOnResult(Attachment(Kind.VIDEO, t.file, t.durationMs))
                        !t.keep -> t.file.delete()
                        else -> {
                            t.file.delete()
                            val message = if (errorOk || t.durationMs in 1 until 700) Res.s(R.string.chat_video_too_short) else finalizeMessage(event.error)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            currentOnResult(null)
                        }
                    }
                }
            }
        }
    }

    // Прив'язка камери. Запис не зупиняється: persistent recording переживає зміну камери.
    DisposableEffect(front) {
        var disposed = false
        switching = true
        providerFuture.addListener({
            if (disposed) return@addListener
            val p = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            fun bind(useFront: Boolean) {
                p.unbindAll()
                val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
            }
            try {
                try {
                    bind(front)
                } catch (e: Exception) {
                    // Деякі телефони не дають записувати відео з однієї з камер — пробуємо іншу, а не закриваємо запис.
                    Log.w(TAG, "bind front=$front failed", e)
                    bind(!front)
                    Toast.makeText(
                        context,
                        if (front) Res.s(R.string.chat_camera_front_unavailable) else Res.s(R.string.chat_camera_back_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
                    currentOnFlip()
                }
                if (take == null) startTake()
            } catch (e: Exception) {
                Log.w(TAG, "camera start failed", e)
                Toast.makeText(context, Res.s(R.string.chat_camera_failed_start), Toast.LENGTH_SHORT).show()
                currentOnResult(null)
            } finally {
                switching = false
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true }
    }

    // Вихід з екрана запису: незбережений запис зупиняємо (persistent recording сам не зупиняється), камеру відпускаємо.
    DisposableEffect(Unit) {
        onDispose {
            take?.let { t -> if (!t.keep) t.recording?.stop() }
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }

    LaunchedEffect(finish) {
        val keep = finish ?: return@LaunchedEffect
        val t = take
        if (t == null) {
            if (keep) Toast.makeText(context, Res.s(R.string.chat_hold_longer_to_record), Toast.LENGTH_SHORT).show()
            currentOnResult(null)
            return@LaunchedEffect
        }
        t.keep = keep
        t.recording?.stop()
        if (!keep) currentOnResult(null)
    }

    Box(modifier.fillMaxSize().background(Glass.Base.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .size(300.dp)
                    .clip(CircleShape)
                    .border(2.dp, Glass.Stroke, CircleShape),
            )
            if (locked && !switching) {
                GlassIconButton(
                    Icons.Default.Cameraswitch,
                    stringResource(R.string.chat_switch_camera),
                    onClick = onFlip,
                    size = 48.dp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}
