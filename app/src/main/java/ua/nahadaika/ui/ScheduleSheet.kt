package ua.nahadaika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.nahadaika.data.Repeat
import ua.nahadaika.formatTime
import ua.nahadaika.repeatLabel
import ua.nahadaika.shortDate
import ua.nahadaika.toLocalDate
import ua.nahadaika.whenLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

/** Через годину, округлено до 5 хвилин. */
private fun defaultTime(): Long {
    val t = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES)
    return t.plusMinutes(((5 - t.minute % 5) % 5).toLong()).toMillis()
}

private data class Preset(val label: String, val at: () -> Long)

private fun presets(): List<Preset> {
    val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    val today = LocalDate.now()
    return buildList {
        add(Preset("Через 30 хв") { now.plusMinutes(30).toMillis() })
        add(Preset("Через 1 год") { now.plusHours(1).toMillis() })
        add(Preset("Через 3 год") { now.plusHours(3).toMillis() })
        if (now.toLocalTime() < LocalTime.of(19, 30)) {
            add(Preset("Сьогодні о 20:00") { today.atTime(20, 0).toMillis() })
        }
        add(Preset("Завтра о 09:00") { today.plusDays(1).atTime(9, 0).toMillis() })
        add(Preset("Завтра о 20:00") { today.plusDays(1).atTime(20, 0).toMillis() })
        add(Preset("Через тиждень") { now.plusWeeks(1).toMillis() })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(
    initialAt: Long?,
    initialRepeat: Repeat,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onDictate: (() -> Unit)? = null,
    onConfirm: (at: Long, repeat: Repeat) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var at by rememberSaveable { mutableLongStateOf(initialAt?.takeIf { it > System.currentTimeMillis() } ?: defaultTime()) }
    var repeat by rememberSaveable { mutableStateOf(initialRepeat) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // Щоб кнопка стала неактивною, якщо обраний час минув, поки вікно відкрите.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val inPast = at <= now

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Коли нагадати?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onDictate != null) {
                    FilledTonalButton(onClick = onDictate) {
                        Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сказати")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets().forEach { preset ->
                    SuggestionChip(onClick = { at = preset.at() }, label = { Text(preset.label) })
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(shortDate(at.toLocalDate()))
                }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(at))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Повторювати", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(repeatLabel(r)) })
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (inPast) "Цей час уже минув — оберіть пізніший" else
                    "Нагадаю ${whenLabel(at)}" + if (repeat != Repeat.NONE) " · ${repeatLabel(repeat).lowercase()}" else "",
                color = if (inPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(at, repeat) },
                enabled = !inPast,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(confirmLabel) }
        }
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = at.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        at = date.atTime(at.toLocalDateTime().toLocalTime()).toMillis()
                    }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Скасувати") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickTime) {
        val current = at.toLocalDateTime()
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    at = current.toLocalDate().atTime(state.hour, state.minute).toMillis()
                    pickTime = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Скасувати") } },
            text = { TimePicker(state = state) },
        )
    }
}
