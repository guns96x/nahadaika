package ua.nahadaika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.nahadaika.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.alarmAt
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.sheetGlow
import ua.nahadaika.whenLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderActions(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onReschedule: () -> Unit,
    onEditText: () -> Unit,
    onSendNow: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Glass.Sheet, scrimColor = Glass.Scrim) {
        Column(
            Modifier.sheetGlow().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (reminder.fired) stringResource(R.string.chat_sent_at, whenLabel(reminder.lastFiredAt ?: reminder.triggerAt))
                else stringResource(R.string.chat_snackbar_reminder, whenLabel(reminder.alarmAt())),
                color = Glass.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            ActionItem(Icons.Default.Schedule, if (reminder.fired) stringResource(R.string.chat_remind_again) else stringResource(R.string.chat_change_time), onClick = onReschedule)
            ActionItem(Icons.Default.Edit, if (reminder.kind == Kind.TEXT) stringResource(R.string.chat_edit_text) else stringResource(R.string.chat_change_caption), onClick = onEditText)
            if (!reminder.fired) ActionItem(Icons.Default.NotificationsActive, stringResource(R.string.chat_send_now), onClick = onSendNow)
            ActionItem(Icons.Default.Delete, stringResource(R.string.chat_delete), danger = true) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.chat_delete_reminder_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.chat_delete), color = Glass.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) Glass.Danger else Glass.Text
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}

/** Діалог правки тексту нагадування або підпису до медіа. */
@Composable
internal fun EditTextDialog(reminder: Reminder, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(reminder.id) { mutableStateOf(reminder.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reminder.kind == Kind.TEXT) stringResource(R.string.chat_reminder_text_title) else stringResource(R.string.chat_caption_title)) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, minLines = 2, maxLines = 8) },
        confirmButton = {
            TextButton(
                enabled = reminder.kind != Kind.TEXT || value.isNotBlank(),
                onClick = { onSave(value) },
            ) { Text(stringResource(R.string.chat_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}
