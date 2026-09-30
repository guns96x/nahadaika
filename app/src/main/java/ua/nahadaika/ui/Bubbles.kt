package ua.nahadaika.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.alarmAt
import ua.nahadaika.formatDuration
import ua.nahadaika.formatTime
import ua.nahadaika.inLabel
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.previewText
import ua.nahadaika.repeatLabel
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.Inter
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.whenLabel
import java.io.File

private val BubbleShape = RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)

@Composable
fun DateHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
            color = Glass.TextFaint,
        )
    }
}

/** Велика картка найближчого нагадування з живим відліком, як у таймері. */
@Composable
fun NextUpCard(reminder: Reminder, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val accent = Glass.Lavender
    val left = (reminder.alarmAt() - now).coerceAtLeast(0)
    val totalSec = left / 1000
    val countdown = if (totalSec < 24 * 3600) {
        "%02d:%02d:%02d".format(totalSec / 3600, totalSec / 60 % 60, totalSec % 60)
    } else {
        "${totalSec / 86400} дн ${totalSec / 3600 % 24} год"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(30.dp))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent), center, size.width * 0.6f),
                    size.width * 0.6f,
                    center,
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "НАЙБЛИЖЧЕ",
            color = Glass.Lavender,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        Text(
            countdown,
            color = Glass.Text,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = 58.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFeatureSettings = "tnum",
                letterSpacing = (-1).sp,
            ),
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        Text(
            previewText(reminder).ifBlank { "Нагадування" },
            color = Glass.Text,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(whenLabel(reminder.alarmAt()).replaceFirstChar { it.uppercase() }, color = Glass.TextDim, fontSize = 13.sp)
    }
}

@Composable
fun ReminderBubble(
    reminder: Reminder,
    highlighted: Boolean,
    player: AudioPlayer,
    now: Long,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    val tint by animateColorAsState(
        if (highlighted) Glass.Lavender.copy(alpha = 0.16f) else Color.Transparent,
        label = "highlight",
    )
    val fill: Brush = SolidColor(if (reminder.fired) Glass.FillSubtle else Glass.Fill)
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .glass(BubbleShape, fill)
                .background(tint)
                .clickable(onClick = onClick),
        ) {
            when (reminder.kind) {
                Kind.PHOTO -> AsyncImage(
                    model = reminder.mediaPath?.let(::File),
                    contentDescription = "Фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = onOpenMedia),
                )
                Kind.VIDEO -> Box(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .clickable(onClick = onOpenMedia),
                ) {
                    AsyncImage(
                        model = reminder.mediaPath?.let(::File),
                        contentDescription = "Відео",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black),
                    )
                    Box(
                        Modifier.align(Alignment.Center).size(54.dp).glass(CircleShape, Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                    Text(
                        formatDuration(reminder.durationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .glass(Glass.Pill, Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Kind.VOICE -> VoiceContent(reminder, player)
                Kind.TEXT -> Unit
            }
            if (reminder.text.isNotBlank()) {
                Text(
                    reminder.text,
                    color = if (reminder.fired) Glass.TextDim else Glass.Text,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
                )
            }
            Footer(reminder, now)
        }
    }
}

@Composable
private fun VoiceContent(reminder: Reminder, player: AudioPlayer) {
    val path = reminder.mediaPath ?: return
    val active = player.currentPath == path
    Row(
        Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp).width(240.dp),
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

@Composable
private fun Footer(r: Reminder, now: Long) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!r.fired) {
            // Скільки лишилось — графічна «пігулка» зліва.
            Text(inLabel(r.alarmAt() - now), fontSize = 12.sp, color = Glass.Lavender)
            Spacer(Modifier.weight(1f))
        }
        if (r.repeat != Repeat.NONE) {
            Icon(Icons.Default.Repeat, null, Modifier.size(13.dp), tint = Glass.TextDim)
            Spacer(Modifier.width(2.dp))
            Text(repeatLabel(r.repeat).lowercase(), fontSize = 12.sp, color = Glass.TextDim)
            Spacer(Modifier.width(6.dp))
        }
        if (r.snoozedUntil != null) {
            Icon(Icons.Default.Snooze, null, Modifier.size(13.dp), tint = Glass.TextDim)
            Spacer(Modifier.width(2.dp))
        }
        val time = if (r.fired) r.lastFiredAt ?: r.triggerAt else r.alarmAt()
        Text(formatTime(time), fontSize = 12.sp, color = Glass.TextFaint)
        Spacer(Modifier.width(3.dp))
        Icon(
            if (r.fired) Icons.Default.DoneAll else Icons.Default.Schedule,
            contentDescription = if (r.fired) "Надіслано" else "Заплановано",
            modifier = Modifier.size(14.dp),
            tint = if (r.fired) Glass.Lavender else Glass.TextFaint,
        )
    }
}

/** Повноекранний перегляд фото або відео. */
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
                Kind.VIDEO -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoPath(path)
                            setOnPreparedListener { start() }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxWidth(),
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
