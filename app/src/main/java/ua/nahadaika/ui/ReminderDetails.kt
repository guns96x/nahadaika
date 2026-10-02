package ua.nahadaika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.nahadaika.data.Comment
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repo
import ua.nahadaika.formatTime
import ua.nahadaika.shortWhen
import ua.nahadaika.toLocalDate
import java.time.LocalDate
import ua.nahadaika.ui.theme.Glass

/** Вміст розгорнутої картки під медіа: швидкі дії й обговорення. */
@Composable
internal fun ReminderExpanded(
    reminder: Reminder,
    onReschedule: () -> Unit,
    onDone: () -> Unit,
    onEditText: () -> Unit,
    onMore: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (reminder.fired) {
            ActionChip(Icons.Default.Replay, "Ще раз", onReschedule)
        } else {
            ActionChip(Icons.Default.Check, "Готово", onDone)
            ActionChip(Icons.Default.Schedule, "Час", onReschedule)
        }
        ActionChip(Icons.Default.Edit, "Текст", onEditText)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = "Більше дій", onClick = onMore),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.MoreHoriz, "Більше дій", tint = Glass.TextDim, modifier = Modifier.size(20.dp)) }
    }
    Discussion(reminder.id)
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Glass.Pill)
            .background(Glass.FillStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = Glass.Text)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = Glass.Text, fontWeight = FontWeight.Medium)
    }
}

/** Обговорення під нагадуванням: повідомлення й поле вводу. Відкрили — нове вважається прочитаним. */
@Composable
private fun Discussion(reminderId: Long) {
    val scope = rememberCoroutineScope()
    val comments by Repo.comments(reminderId).collectAsStateWithLifecycle(emptyList())
    var draft by rememberSaveable(reminderId) { mutableStateOf("") }

    LaunchedEffect(reminderId, comments.size) { Repo.markCommentsRead(reminderId) }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        scope.launch { Repo.addComment(reminderId, text) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        comments.forEach { CommentBubble(it) }
        val style = LocalTextStyle.current.copy(color = Glass.Text, fontSize = 14.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(14.dp)).background(Glass.Fill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = style,
                cursorBrush = SolidColor(Glass.Lavender),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f).heightIn(min = 36.dp).padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(if (comments.isEmpty()) "Обговорити…" else "Відповісти…", style = style, color = Glass.TextFaint)
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(enabled = draft.isNotBlank(), onClickLabel = "Надіслати", onClick = ::send),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "Надіслати",
                    tint = if (draft.isNotBlank()) Glass.Lavender else Glass.TextFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Моє — праворуч, чуже — ліворуч з іменем, як у месенджері. */
@Composable
private fun CommentBubble(c: Comment) {
    val mine = c.authorName == null
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) Glass.Lavender.copy(alpha = 0.20f) else Glass.FillStrong)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            c.authorName?.let { Text(it, fontSize = 12.sp, color = Glass.Lavender, fontWeight = FontWeight.SemiBold) }
            // Час — у кінці рядка, як у месенджерах: бульбашка не росте на зайвий рядок.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(c.text, fontSize = 14.sp, color = Glass.Text, lineHeight = 19.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (c.createdAt.toLocalDate() == LocalDate.now()) formatTime(c.createdAt) else shortWhen(c.createdAt) + " " + formatTime(c.createdAt),
                    fontSize = 11.sp,
                    color = Glass.TextFaint,
                )
            }
        }
    }
}
