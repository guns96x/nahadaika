package ua.nahadaika.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import ua.nahadaika.data.Kind
import ua.nahadaika.formatDuration
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val BarHeight = 52.dp

/** Поточний запис: що пишемо і чи «замкнено» (пишеться без утримання). */
internal data class Rec(val kind: Kind, val locked: Boolean, val startedAt: Long = System.currentTimeMillis())

internal val LockDistance = 90.dp
internal val CancelDistance = 120.dp

@Composable
internal fun Composer(
    haze: HazeState,
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    recordMode: Kind,
    rec: Rec?,
    elapsed: Long,
    dragX: Float,
    dragY: Float,
    onAttach: (Kind?) -> Unit,
    onSend: () -> Unit,
    onToggleMode: () -> Unit,
    onHoldStart: () -> Boolean,
    onDrag: (Float, Float) -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            if (rec != null) {
                RecordingPill(haze, rec, elapsed, dragX, onCancel)
            } else {
                InputPill(haze, text, onTextChange, onAttach)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Одна й та сама кнопка весь час — щоб жест утримання не переривався.
        RecordButton(
            canSend = canSend,
            recordMode = recordMode,
            rec = rec,
            dragY = dragY,
            onSend = onSend,
            onToggleMode = onToggleMode,
            onHoldStart = onHoldStart,
            onDrag = onDrag,
            onLock = onLock,
            onRelease = onRelease,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun InputPill(
    haze: HazeState,
    text: String,
    onTextChange: (String) -> Unit,
    onAttach: (Kind?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Іконки прив'язані до низу, як у Telegram: при багаторядковому тексті вони лишаються біля кнопки.
    Row(
        Modifier.fillMaxWidth().heightIn(min = BarHeight).glassHaze(haze, RoundedCornerShape(BarHeight / 2)),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box {
            BarIcon(Icons.Default.AttachFile, "Додати", Glass.TextDim) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Default.Videocam, "Записати відео") { menu = false; onAttach(Kind.VIDEO) }
                MenuItem(Icons.Default.CameraAlt, "Зняти фото") { menu = false; onAttach(Kind.PHOTO) }
                MenuItem(Icons.Default.PhotoLibrary, "З галереї") { menu = false; onAttach(null) }
            }
        }
        val style = LocalTextStyle.current.copy(color = Glass.Text, fontSize = 16.sp, lineHeight = 22.sp)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = style,
            cursorBrush = SolidColor(Glass.Lavender),
            maxLines = 6,
            modifier = Modifier.weight(1f).padding(vertical = 15.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Нагадування", style = style, color = Glass.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
    }
}

/** Смуга запису: ● таймер і «‹ посуньте, щоб скасувати» (або «Скасувати» в режимі 🔒). */
@Composable
private fun RecordingPill(haze: HazeState, rec: Rec, elapsed: Long, dragX: Float, onCancel: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    val cancelPx = with(LocalDensity.current) { CancelDistance.toPx() }
    Row(
        Modifier.fillMaxWidth().height(BarHeight).glassHaze(haze, RoundedCornerShape(BarHeight / 2)).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).background(Glass.Danger, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration(elapsed),
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            color = Glass.Text,
            fontSize = 16.sp,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (rec.locked) {
                TextButton(onClick = onCancel) { Text("Скасувати", color = Glass.Danger, fontSize = 15.sp) }
            } else {
                val progress = (-dragX / cancelPx).coerceIn(0f, 1f)
                Row(
                    Modifier
                        .offset { IntOffset((dragX * 0.6f).toInt(), 0) }
                        .alpha(1f - progress * 0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Glass.TextFaint, modifier = Modifier.size(20.dp))
                    Text("Посуньте, щоб скасувати", color = Glass.TextDim, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Кнопка запису, як у Telegram: тап — перемкнути 🎤 ↔ 📹; утримання — запис (відпустили — готово);
 * утримуючи, потягнути вгору до 🔒 — запис без рук; потягнути вліво — скасувати.
 * Коли є текст або вкладення — кнопка «запланувати»; у режимі 🔒 — «готово».
 */
@Composable
private fun RecordButton(
    canSend: Boolean,
    recordMode: Kind,
    rec: Rec?,
    dragY: Float,
    onSend: () -> Unit,
    onToggleMode: () -> Unit,
    onHoldStart: () -> Boolean,
    onDrag: (Float, Float) -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lockPx = with(density) { LockDistance.toPx() }
    val cancelPx = with(density) { CancelDistance.toPx() }
    val state by rememberUpdatedState(Triple(canSend, rec, recordMode))
    val callbacks by rememberUpdatedState(
        listOf(onSend, onToggleMode, onLock, onRelease, onCancel),
    )
    val holdStart by rememberUpdatedState(onHoldStart)
    val drag by rememberUpdatedState(onDrag)

    val holding = rec != null && !rec.locked
    val scale by animateFloatAsState(if (holding) 1.2f else 1f, label = "scale")

    Box(contentAlignment = Alignment.Center) {
        if (holding) LockHint(progress = (-dragY / lockPx).coerceIn(0f, 1f))
        Box(
            Modifier
                .size(BarHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Glass.Primary)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val (send, current, _) = state
                        val (sendCb, toggleCb, lockCb, releaseCb, cancelCb) = callbacks
                        if (send || current?.locked == true) {
                            // Звичайна кнопка: «запланувати» або «готово» в режимі 🔒.
                            if (waitForUpOrCancellation() != null) {
                                if (send) sendCb() else releaseCb()
                            }
                            return@awaitEachGesture
                        }
                        // Короткий тап чи утримання?
                        val tap = withTimeoutOrNull(220L) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                if (!change.pressed) return@withTimeoutOrNull true
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    return@withTimeoutOrNull false
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        } ?: false
                        if (tap) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            toggleCb()
                            return@awaitEachGesture
                        }
                        if (!holdStart()) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                releaseCb()
                                break
                            }
                            change.consume()
                            val d = change.position - down.position
                            drag(d.x.coerceAtMost(0f), d.y.coerceAtMost(0f))
                            if (-d.y > lockPx) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lockCb()
                                // Палець ще на екрані — просто чекаємо, поки відпустять.
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                                break
                            }
                            if (-d.x > cancelPx) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                cancelCb()
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                canSend -> Icons.Default.AlarmAdd
                rec?.locked == true -> Icons.Default.Check
                (rec?.kind ?: recordMode) == Kind.VIDEO -> Icons.Default.Videocam
                else -> Icons.Default.Mic
            }
            Crossfade(targetState = icon, label = "icon") { current ->
                Icon(
                    current,
                    contentDescription = when (current) {
                        Icons.Default.AlarmAdd -> "Запланувати"
                        Icons.Default.Check -> "Готово"
                        Icons.Default.Videocam -> "Відео: тап — перемкнути, утримати — записати"
                        else -> "Голосове: тап — перемкнути, утримати — записати"
                    },
                    tint = Glass.OnPrimary,
                )
            }
        }
    }
}

/** Скляна «капсула» з замочком над кнопкою: тягніть угору, щоб писати без утримання. */
@Composable
private fun LockHint(progress: Float) {
    Column(
        Modifier
            .offset { IntOffset(0, (-(BarHeight.toPx() * 1.55f) - progress * 36.dp.toPx()).toInt()) }
            .width(40.dp)
            .glass(RoundedCornerShape(20.dp), Glass.Sheet)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (progress > 0.85f) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = "Потягніть угору, щоб зафіксувати",
            tint = if (progress > 0.85f) Glass.Lavender else Glass.Text,
            modifier = Modifier.size(20.dp),
        )
        Icon(Icons.Default.KeyboardArrowUp, null, tint = Glass.TextFaint, modifier = Modifier.size(18.dp).alpha(1f - progress))
    }
}

/** Іконка в полі вводу: квадрат висотою з поле, щоб усе стояло на одній лінії. */
@Composable
private fun BarIcon(icon: ImageVector, contentDescription: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(BarHeight).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
internal fun AttachmentPreview(attachment: Attachment, player: AudioPlayer, haze: HazeState, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassHaze(haze, RoundedCornerShape(22.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (attachment.kind) {
            Kind.PHOTO, Kind.VIDEO -> AsyncImage(
                model = attachment.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
            )
            Kind.VOICE -> {
                val path = attachment.file.absolutePath
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Glass.Primary).clickable { player.toggle(path) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (player.currentPath == path && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Прослухати",
                        tint = Glass.OnPrimary,
                    )
                }
            }
            Kind.TEXT -> Unit
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (attachment.kind) {
                    Kind.PHOTO -> "Фото"
                    Kind.VIDEO -> "Відео · ${formatDuration(attachment.durationMs)}"
                    Kind.VOICE -> "Голосове · ${formatDuration(attachment.durationMs)}"
                    Kind.TEXT -> ""
                },
                color = Glass.Text,
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Прибрати", tint = Glass.TextDim) }
    }
}
