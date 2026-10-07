package ua.nahadaika.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat as RepeatIcon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ua.nahadaika.ui.theme.PrimaryButton
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassChip
import ua.nahadaika.ui.theme.GlassSegmented
import ua.nahadaika.ui.theme.sheetGlow
import ua.nahadaika.Prefs
import ua.nahadaika.data.Repeat
import ua.nahadaika.inLabel
import ua.nahadaika.repeatLabel
import ua.nahadaika.toLocalDate
import ua.nahadaika.whenLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val MODE_IN = 0
private const val MODE_AT = 1
private const val MAX_DAYS = 366
private const val MAX_TIMER_SECONDS = 99 * 3600 + 59 * 60 + 59

private val dayWheelFmt = DateTimeFormatter.ofPattern("EE, d MMM", Locale.forLanguageTag("uk"))

private fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

/** Вгору до цілої хвилини — барабани «Коли» не мають секунд. */
private fun ceilToMinute(ms: Long): Long {
    val t = ms.toLocalDateTime()
    val floor = t.truncatedTo(ChronoUnit.MINUTES)
    return (if (floor == t) floor else floor.plusMinutes(1)).toMillis()
}

/** Через годину, округлено до 5 хвилин. */
private fun defaultTime(): Long {
    val t = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES)
    return t.plusMinutes(((5 - t.minute % 5) % 5).toLong()).toMillis()
}

private fun dayWheelLabel(index: Int): String = when (index) {
    0 -> "Сьогодні"
    1 -> "Завтра"
    else -> LocalDate.now().plusDays(index.toLong()).format(dayWheelFmt)
}

private data class AtPreset(val label: String, val at: () -> Long)

private fun atPresets(defaultHour: Int): List<AtPreset> {
    val today = LocalDate.now()
    return buildList {
        if (LocalTime.now() < LocalTime.of(19, 30)) add(AtPreset("Сьогодні 20:00") { today.atTime(20, 0).toMillis() })
        add(AtPreset("Завтра %02d:00".format(defaultHour)) { today.plusDays(1).atTime(defaultHour, 0).toMillis() })
        add(AtPreset("Завтра 20:00") { today.plusDays(1).atTime(20, 0).toMillis() })
        add(AtPreset("Через тиждень") {
            LocalDateTime.now().plusWeeks(1).truncatedTo(ChronoUnit.MINUTES).toMillis()
        })
    }
}

private val timerPresets = listOf("5 хв" to 5 * 60, "10 хв" to 10 * 60, "15 хв" to 15 * 60, "30 хв" to 30 * 60, "1 год" to 3600, "2 год" to 7200)

/**
 * Вибір часу нагадування. Дві вкладки з одним спільним часом:
 * «Через» — барабани годин/хвилин/секунд, як таймер Samsung;
 * «Коли» — барабани дня/години/хвилини, як «Надіслати пізніше» в Telegram.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    initialAt: Long?,
    initialRepeat: Repeat,
    confirmLabel: String,
    onDismiss: () -> Unit,
    alarm: Boolean? = null,
    onAlarmChange: (Boolean) -> Unit = {},
    onConfirm: (at: Long, repeat: Repeat) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val validInitial = initialAt?.takeIf { it > System.currentTimeMillis() }
    var mode by rememberSaveable { mutableIntStateOf(if (validInitial != null) MODE_AT else Prefs.scheduleMode(context)) }
    var at by rememberSaveable { mutableLongStateOf(validInitial?.let(::ceilToMinute) ?: defaultTime()) }
    var timer by rememberSaveable { mutableIntStateOf(Prefs.timerSeconds(context)) }
    var repeat by rememberSaveable { mutableStateOf(initialRepeat) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val effectiveAt = if (mode == MODE_IN) now + timer * 1000L else at
    val invalid = if (mode == MODE_IN) timer <= 0 else at <= now

    // Перемикання вкладок переносить час: «через 1 год 17 хв» ↔ «сьогодні о 11:52».
    fun switchMode(target: Int) {
        if (target == mode) return
        if (target == MODE_AT) {
            if (timer > 0) at = ceilToMinute(System.currentTimeMillis() + timer * 1000L)
        } else {
            val left = (at - System.currentTimeMillis()) / 1000
            if (left in 60..MAX_TIMER_SECONDS.toLong()) timer = (left / 60 * 60).toInt()
        }
        mode = target
        Prefs.setScheduleMode(context, target)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Glass.Sheet,
        scrimColor = Glass.Scrim,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetGlow()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Коли нагадати?",
                    color = Glass.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            GlassSegmented(
                options = listOf(Icons.Default.HourglassBottom to "Через", Icons.Default.CalendarMonth to "Коли"),
                selected = mode,
                onSelect = { switchMode(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (mode == MODE_IN) {
                TimerWheels(timer, onChange = { timer = it })
                ChipsRow {
                    timerPresets.forEach { (label, seconds) ->
                        GlassChip(label, onClick = { timer = seconds })
                    }
                }
            } else {
                DateTimeWheels(at, onChange = { at = it })
                ChipsRow {
                    atPresets(Prefs.defaultHour(context)).forEach { preset ->
                        GlassChip(preset.label, onClick = { at = preset.at() })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatChip(repeat, onChange = { repeat = it })
                if (alarm != null) {
                    GlassChip(
                        if (alarm) "Будильник" else "Як будильник",
                        onClick = { onAlarmChange(!alarm) },
                        selected = alarm,
                        leading = if (alarm) Icons.Default.Alarm else Icons.Default.AlarmOff,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    invalid && mode == MODE_IN -> "Накрутіть, через скільки нагадати"
                    invalid -> "Цей час уже минув — оберіть пізніший"
                    mode == MODE_IN -> "Нагадаю ${whenLabel(effectiveAt)}"
                    else -> inLabel(effectiveAt - now).replaceFirstChar { it.uppercase() }
                },
                color = if (invalid) Glass.Danger else Glass.TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (invalid) confirmLabel
                else if (mode == MODE_IN) "$confirmLabel ${inLabel(timer * 1000L)}"
                else "$confirmLabel · ${whenLabel(at)}",
                enabled = !invalid,
                onClick = {
                    if (mode == MODE_IN) {
                        Prefs.setTimerSeconds(context, timer)
                        onConfirm(System.currentTimeMillis() + timer * 1000L, repeat)
                    } else {
                        onConfirm(at, repeat)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimerWheels(seconds: Int, onChange: (Int) -> Unit) {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("Години", "Хвилини", "Секунди").forEach {
                Text(
                    it,
                    fontSize = 13.sp,
                    color = Glass.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().wheelSelectionBand(), verticalAlignment = Alignment.CenterVertically) {
            // Барабани читають актуальне значення в момент зміни, щоб не затерти сусідні.
            val current by rememberUpdatedState(seconds)
            WheelPicker(count = 100, value = h, onValueChange = { onChange(it * 3600 + current % 3600) }, fontSize = 34.sp, modifier = Modifier.weight(1f))
            Colon()
            WheelPicker(count = 60, value = m, onValueChange = { onChange(current / 3600 * 3600 + it * 60 + current % 60) }, fontSize = 34.sp, modifier = Modifier.weight(1f))
            Colon()
            WheelPicker(count = 60, value = s, onValueChange = { onChange(current / 60 * 60 + it) }, fontSize = 34.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Colon() {
    Text(":", fontSize = 28.sp, fontWeight = FontWeight.ExtraLight, color = Glass.TextDim)
}

@Composable
private fun DateTimeWheels(at: Long, onChange: (Long) -> Unit) {
    val today = LocalDate.now()
    val t = at.toLocalDateTime()
    val day = ChronoUnit.DAYS.between(today, at.toLocalDate()).toInt().coerceIn(0, MAX_DAYS - 1)
    val current by rememberUpdatedState(at)
    Row(Modifier.fillMaxWidth().wheelSelectionBand(), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            count = MAX_DAYS,
            value = day,
            loop = false,
            fontSize = 21.sp,
            label = ::dayWheelLabel,
            onValueChange = { d -> onChange(today.plusDays(d.toLong()).atTime(current.toLocalDateTime().toLocalTime()).toMillis()) },
            modifier = Modifier.weight(2f),
        )
        WheelPicker(
            count = 24,
            value = t.hour,
            onValueChange = { h -> onChange(current.toLocalDateTime().withHour(h).toMillis()) },
            modifier = Modifier.weight(1f),
        )
        WheelPicker(
            count = 60,
            value = t.minute,
            onValueChange = { m -> onChange(current.toLocalDateTime().withMinute(m).toMillis()) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChipsRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun RepeatChip(repeat: Repeat, onChange: (Repeat) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassChip(
            "Повторювати: " + if (repeat == Repeat.NONE) "ніколи" else repeatLabel(repeat).lowercase(),
            onClick = { open = true },
            leading = Icons.Default.RepeatIcon,
            trailing = Icons.Default.ArrowDropDown,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Repeat.entries.forEach { r ->
                DropdownMenuItem(text = { Text(repeatLabel(r)) }, onClick = {
                    onChange(r)
                    open = false
                })
            }
        }
    }
}
