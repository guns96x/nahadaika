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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.alarmAt
import ua.nahadaika.formatDuration
import ua.nahadaika.formatTime
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.repeatLabel
import java.io.File

@Composable
fun DateHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ReminderBubble(
    reminder: Reminder,
    highlighted: Boolean,
    player: AudioPlayer,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    val base = MaterialTheme.colorScheme.primaryContainer
    val color by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else base,
        label = "highlight",
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = color,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).clickable(onClick = onClick),
        ) {
            Column {
                when (reminder.kind) {
                    Kind.PHOTO -> AsyncImage(
                        model = reminder.mediaPath?.let(::File),
                        contentDescription = "Фото",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black.copy(alpha = 0.08f)).clickable(onClick = onOpenMedia),
                    )
                    Kind.VIDEO -> Box(Modifier.clickable(onClick = onOpenMedia)) {
                        AsyncImage(
                            model = reminder.mediaPath?.let(::File),
                            contentDescription = "Відео",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black),
                        )
                        Box(
                            Modifier.align(Alignment.Center).size(52.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                        Text(
                            formatDuration(reminder.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Kind.VOICE -> VoiceContent(reminder, player)
                    Kind.TEXT -> Unit
                }
                if (reminder.text.isNotBlank()) {
                    Text(
                        reminder.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                }
                Footer(reminder)
            }
        }
    }
}

@Composable
private fun VoiceContent(reminder: Reminder, player: AudioPlayer) {
    val path = reminder.mediaPath ?: return
    val active = player.currentPath == path
    Row(
        Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp).width(240.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { player.toggle(path) }, modifier = Modifier.size(44.dp)) {
            Icon(if (active && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Відтворити")
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (active) player.progress else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                formatDuration(reminder.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Footer(r: Reminder) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r.repeat != Repeat.NONE) {
            Icon(Icons.Default.Repeat, null, Modifier.size(13.dp), tint = muted)
            Spacer(Modifier.width(2.dp))
            Text(repeatLabel(r.repeat).lowercase(), style = MaterialTheme.typography.labelSmall, color = muted)
            Spacer(Modifier.width(6.dp))
        }
        if (r.snoozedUntil != null) {
            Icon(Icons.Default.Snooze, null, Modifier.size(13.dp), tint = muted)
            Spacer(Modifier.width(2.dp))
        }
        val time = if (r.fired) r.lastFiredAt ?: r.triggerAt else r.alarmAt()
        Text(formatTime(time), style = MaterialTheme.typography.labelSmall, color = muted)
        Spacer(Modifier.width(3.dp))
        Icon(
            if (r.fired) Icons.Default.DoneAll else Icons.Default.Schedule,
            contentDescription = if (r.fired) "Надіслано" else "Заплановано",
            modifier = Modifier.size(14.dp),
            tint = if (r.fired) MaterialTheme.colorScheme.primary else muted,
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
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                )
            }
        }
    }
}
