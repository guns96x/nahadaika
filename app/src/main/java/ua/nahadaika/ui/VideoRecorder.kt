package ua.nahadaika.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.data.Kind
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.MediaFiles
import java.io.File

/** Один запис: файл і рішення, чи зберегти його після зупинки. */
private class Take(val file: File) {
    var keep = false
    var durationMs = 0L
    var recording: Recording? = null
}

/**
 * Відео-«кружечок», як у Telegram: камера відкривається й запис стартує одразу.
 * [finish]: null — іде запис; true — зберегти; false — відкинути. Результат — у [onResult].
 * [front] змінюється кнопкою ⟲ (лише в режимі 🔒) — запис починається наново з іншої камери.
 */
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
    var take by remember { mutableStateOf<Take?>(null) }

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

    fun startTake(attempt: Int = 0) {
        val t = Take(MediaFiles.newFile(context, "mp4"))
        val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(t.file).build())
        val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        currentOnElapsed(0)
        t.recording = try {
            (if (audioGranted) pending.withAudioEnabled() else pending).start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> {
                        t.durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        if (take === t) currentOnElapsed(t.durationMs)
                    }
                    is VideoRecordEvent.Finalize -> {
                        val ok = t.keep && t.file.length() > 0 && t.durationMs >= 700 &&
                            (event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                                event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                        if (ok) {
                            currentOnResult(Attachment(Kind.VIDEO, t.file, t.durationMs))
                        } else {
                            t.file.delete()
                            if (t.keep) {
                                Toast.makeText(context, "Відео занадто коротке", Toast.LENGTH_SHORT).show()
                                currentOnResult(null)
                            }
                        }
                    }
                }
            }
        } catch (_: IllegalStateException) {
            // Попередній запис (після зміни камери) ще завершується — пробуємо трохи пізніше.
            t.file.delete()
            if (attempt < 10) previewView.postDelayed({ startTake(attempt + 1) }, 200) else currentOnResult(null)
            return
        }
        take = t
    }

    // Прив'язка камери; при зміні камери поточний запис відкидається і починається новий.
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                startTake()
            } catch (_: Exception) {
                Toast.makeText(context, "Не вдалося увімкнути камеру", Toast.LENGTH_SHORT).show()
                currentOnResult(null)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            take?.let { t -> if (!t.keep) t.recording?.stop() }
            provider?.unbindAll()
        }
    }

    LaunchedEffect(finish) {
        val keep = finish ?: return@LaunchedEffect
        val t = take
        if (t == null) {
            if (keep) Toast.makeText(context, "Утримуйте кнопку довше, щоб записати", Toast.LENGTH_SHORT).show()
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
            if (locked) {
                GlassIconButton(
                    Icons.Default.Cameraswitch,
                    "Змінити камеру",
                    onClick = onFlip,
                    size = 48.dp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}
