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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ua.nahadaika.Prefs
import ua.nahadaika.ui.theme.PrimaryCircle
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.data.Kind
import ua.nahadaika.formatDuration
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
 * Повноекранний запис відео, як «кружечки» в Telegram: запис стартує одразу,
 * ✓ — зберегти, ✕ — скасувати, ⟲ — перемкнути камеру (почне запис наново).
 */
@SuppressLint("MissingPermission") // дозвіл на камеру перевіряється перед відкриттям, на мікрофон — нижче
@Composable
fun VideoRecorderDialog(onDone: (Attachment) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnCancel by rememberUpdatedState(onCancel)

    var front by remember { mutableStateOf(Prefs.frontCamera(context)) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var take by remember { mutableStateOf<Take?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
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
        elapsed = 0
        t.recording = try {
            (if (audioGranted) pending.withAudioEnabled() else pending).start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> {
                        t.durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        if (take === t) elapsed = t.durationMs
                    }
                    is VideoRecordEvent.Finalize -> {
                        val ok = t.keep && t.file.length() > 0 && t.durationMs >= 700 &&
                            (event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                                event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE)
                        if (ok) {
                            currentOnDone(Attachment(Kind.VIDEO, t.file, t.durationMs))
                        } else {
                            t.file.delete()
                            if (t.keep) {
                                Toast.makeText(context, "Відео занадто коротке", Toast.LENGTH_SHORT).show()
                                currentOnCancel()
                            }
                        }
                    }
                }
            }
        } catch (_: IllegalStateException) {
            // Попередній запис (після зміни камери) ще завершується — пробуємо трохи пізніше.
            t.file.delete()
            if (attempt < 10) previewView.postDelayed({ startTake(attempt + 1) }, 200) else currentOnCancel()
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
                currentOnCancel()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            take?.let { t ->
                if (!t.keep) t.recording?.stop()
            }
            provider?.unbindAll()
        }
    }

    fun finish(keep: Boolean) {
        val t = take ?: return currentOnCancel()
        t.keep = keep
        t.recording?.stop()
        if (!keep) currentOnCancel()
    }

    Dialog(
        onDismissRequest = { finish(keep = false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "pulse",
            )
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .glass(Glass.Pill, Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).alpha(pulse).background(Color.Red, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(elapsed), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(onClick = { finish(keep = false) }) {
                    Icon(Icons.Default.Close, "Скасувати", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Box(
                    Modifier
                        .size(88.dp)
                        .glass(CircleShape, Color.White.copy(alpha = 0.12f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PrimaryCircle(onClick = { finish(keep = true) }, size = 72.dp) {
                        Icon(Icons.Default.Check, "Готово", modifier = Modifier.size(34.dp))
                    }
                }
                RoundButton(onClick = {
                    front = !front
                    Prefs.setFrontCamera(context, front)
                }) {
                    Icon(Icons.Default.Cameraswitch, "Змінити камеру", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(58.dp).glass(CircleShape, Color.Black.copy(alpha = 0.30f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
