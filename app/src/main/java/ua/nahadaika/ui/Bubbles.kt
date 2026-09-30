package ua.nahadaika.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.max
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.formatDuration
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.glass
import java.io.File

@Composable
internal fun VoicePlayer(reminder: Reminder, player: AudioPlayer, modifier: Modifier = Modifier) {
    val path = reminder.mediaPath ?: return
    val active = player.currentPath == path
    Row(
        modifier.widthIn(max = 280.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Glass.Primary).clickable { player.toggle(path) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Відтворити",
                tint = Glass.OnPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (active) player.progress else 0f },
                color = Glass.Text,
                trackColor = Glass.FillStrong,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(Glass.Pill),
            )
            Spacer(Modifier.size(6.dp))
            Text(formatDuration(reminder.durationMs), fontSize = 12.sp, color = Glass.TextDim)
        }
    }
}

/** Відео-нагадування: кадр-обкладинка з ▶; після тапу грає прямо тут, без окремого вікна. */
@Composable
internal fun VideoMedia(
    reminder: Reminder,
    playing: Boolean,
    onPlay: () -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        // Кадр-обкладинка лежить під плеєром, поки не з'явиться перший кадр відео.
        AsyncImage(
            model = reminder.mediaPath?.let(::File),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val path = reminder.mediaPath
        if (playing && path != null) {
            InlineVideo(path, reminder.durationMs, onEnded = onEnded, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Відтворити відео", onClick = onPlay)) {
                Box(
                    Modifier.align(Alignment.Center).size(54.dp).glass(CircleShape, Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.PlayArrow, "Відтворити відео", tint = Color.White, modifier = Modifier.size(34.dp)) }
                VideoLabel(formatDuration(reminder.durationMs), Modifier.align(Alignment.TopStart))
            }
        }
    }
}

@Composable
private fun VideoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        modifier = modifier
            .padding(8.dp)
            .glass(Glass.Pill, Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Відео, що грає прямо в бульбашці, як у Telegram: тап — пауза/продовжити,
 * внизу — прогрес, у кутку — скільки лишилось. Кадр обрізається по центру під рамку.
 */
@Composable
private fun InlineVideo(path: String, durationMs: Long, onEnded: () -> Unit, modifier: Modifier = Modifier) {
    val ended by rememberUpdatedState(onEnded)
    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(durationMs) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LaunchedEffect(prepared, paused) {
        while (prepared && !paused) {
            position = runCatching { player.currentPosition.toLong() }.getOrDefault(position)
            delay(100)
        }
    }

    Box(
        modifier
            .clickable(onClickLabel = if (paused) "Продовжити" else "Пауза") {
                if (!prepared) return@clickable
                paused = !paused
                if (paused) player.pause() else player.start()
            }
            .semantics { contentDescription = if (paused) "Відео на паузі" else "Відео відтворюється" },
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    var videoW = 0
                    var videoH = 0
                    fun crop() {
                        if (width == 0 || height == 0 || videoW == 0 || videoH == 0) return
                        val scale = max(width.toFloat() / videoW, height.toFloat() / videoH)
                        setTransform(
                            Matrix().apply {
                                setScale(videoW * scale / width, videoH * scale / height, width / 2f, height / 2f)
                            },
                        )
                    }
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                            surface = Surface(texture)
                            try {
                                player.setSurface(surface)
                                if (!prepared) {
                                    player.setDataSource(path)
                                    player.setOnVideoSizeChangedListener { _, vw, vh ->
                                        videoW = vw
                                        videoH = vh
                                        crop()
                                    }
                                    player.setOnPreparedListener {
                                        total = it.duration.toLong().takeIf { d -> d > 0 } ?: total
                                        prepared = true
                                        it.start()
                                    }
                                    player.setOnCompletionListener { ended() }
                                    player.setOnErrorListener { _, _, _ ->
                                        ended()
                                        true
                                    }
                                    player.prepareAsync()
                                }
                            } catch (_: Exception) {
                                ended()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) = crop()

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (paused) {
            Box(
                Modifier.align(Alignment.Center).size(54.dp).glass(CircleShape, Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
        }
        VideoLabel(formatDuration((total - position).coerceAtLeast(0)), Modifier.align(Alignment.TopStart))
        LinearProgressIndicator(
            progress = { if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f),
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
        )
    }
}

/** Повноекранний перегляд фото. */
@Composable
fun MediaViewer(reminder: Reminder, onDismiss: () -> Unit) {
    val path = reminder.mediaPath ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when (reminder.kind) {
                Kind.PHOTO -> AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Unit
            }
            if (reminder.text.isNotBlank()) {
                Text(
                    reminder.text,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(20.dp), Color.Black.copy(alpha = 0.4f))
                        .padding(16.dp),
                )
            }
        }
    }
}
