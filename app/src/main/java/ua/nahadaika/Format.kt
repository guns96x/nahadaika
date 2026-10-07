package ua.nahadaika

import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Формати будуються за поточною мовою, бо вона може змінитися без перезапуску процесу.
private fun fmt(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt("HH:mm"))

/** "Сьогодні", "Завтра", "5 жовтня", "5 жовтня 2027". */
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> Res.s(R.string.fmt_today)
        today.plusDays(1) -> Res.s(R.string.fmt_tomorrow)
        today.minusDays(1) -> Res.s(R.string.fmt_yesterday)
        else -> date.format(fmt(if (date.year == today.year) "d MMMM" else "d MMMM yyyy"))
    }
}

/** "сьогодні о 20:00", "5 жовтня о 09:00". */
fun whenLabel(ms: Long): String {
    val day = dayLabel(ms.toLocalDate()).replaceFirstChar { it.lowercase(Locale.getDefault()) }
    return Res.s(R.string.fmt_day_at, day, formatTime(ms))
}

/** Для підтвердження: «через 10 с», «через 25 хв» — якщо скоро, інакше «завтра о 9:00». */
fun soonLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = (ms - now + 500) / 1000
    return when {
        d in 0 until 60 -> Res.s(R.string.fmt_in_sec, d)
        d in 60 until 600 && d % 60 != 0L -> Res.s(R.string.fmt_in_min_sec, d / 60, d % 60)
        d in 60 until 3600 -> Res.s(R.string.fmt_in_min, (d + 30) / 60)
        else -> whenLabel(ms)
    }
}

/** Коротко для списку чатів: "20:00", "завтра", "5 жовт.". */
fun shortWhen(ms: Long): String {
    val date = ms.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> formatTime(ms)
        today.plusDays(1) -> Res.s(R.string.fmt_tomorrow_short)
        else -> date.format(fmt("d MMM"))
    }
}

fun shortDate(date: LocalDate): String = date.format(fmt("EE, d MMM"))

/** «через 1 год 17 хв», «через 45 хв», «через 2 дн 3 год». */
fun inLabel(ms: Long): String {
    val totalMin = (ms + 59_999) / 60_000
    val days = totalMin / (24 * 60)
    val hours = totalMin / 60 % 24
    val minutes = totalMin % 60
    val parts = buildList {
        if (days > 0) add(Res.s(R.string.fmt_unit_days, days))
        if (hours > 0) add(Res.s(R.string.fmt_unit_hours, hours))
        if (minutes > 0 && days == 0L) add(Res.s(R.string.fmt_unit_minutes, minutes))
    }
    return if (parts.isEmpty()) Res.s(R.string.fmt_in_less_than_minute) else Res.s(R.string.fmt_in, parts.joinToString(" "))
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun repeatLabel(repeat: Repeat): String = when (repeat) {
    Repeat.NONE -> Res.s(R.string.fmt_repeat_none)
    Repeat.DAILY -> Res.s(R.string.fmt_repeat_daily)
    Repeat.WEEKLY -> Res.s(R.string.fmt_repeat_weekly)
    Repeat.MONTHLY -> Res.s(R.string.fmt_repeat_monthly)
    Repeat.YEARLY -> Res.s(R.string.fmt_repeat_yearly)
}

/** Опис повідомлення одним рядком — для сповіщень і списку чатів. */
fun previewText(r: Reminder): String {
    val caption = r.text.takeIf { it.isNotBlank() }
    return when (r.kind) {
        Kind.TEXT -> r.text
        Kind.VOICE -> "🎤 " + Res.s(R.string.fmt_voice_paren, formatDuration(r.durationMs)) + (caption?.let { " · $it" } ?: "")
        Kind.VIDEO -> "🎬 " + (caption ?: Res.s(R.string.fmt_video))
        Kind.PHOTO -> "🖼 " + (caption ?: Res.s(R.string.fmt_photo))
    }
}

private const val TITLE_MAX = 40

/**
 * Короткий заголовок для згорнутої картки: перше речення (або рядок) тексту, обрізане по слову.
 * Медіа без підпису — вид і тривалість: «🎤 Голосове · 0:09».
 */
fun reminderTitle(r: Reminder): String {
    val text = r.text.trim()
    val prefix = when (r.kind) {
        Kind.TEXT -> ""
        Kind.VOICE -> "🎤 "
        Kind.VIDEO -> "🎬 "
        Kind.PHOTO -> "🖼 "
    }
    if (text.isEmpty()) {
        return when (r.kind) {
            Kind.TEXT -> Res.s(R.string.fmt_reminder)
            Kind.VOICE -> "🎤 " + Res.s(R.string.fmt_voice_dur, formatDuration(r.durationMs))
            Kind.VIDEO -> "🎬 " + Res.s(R.string.fmt_circle_dur, formatDuration(r.durationMs))
            Kind.PHOTO -> "🖼 " + Res.s(R.string.fmt_photo)
        }
    }
    val firstLine = text.lineSequence().first().trim()
    // Перше речення, якщо воно не надто коротке («Ок.» — не заголовок).
    val sentence = Regex("^(.{12,}?[.!?…])(\\s|$)").find(firstLine)?.groupValues?.get(1) ?: firstLine
    val title = if (sentence.length <= TITLE_MAX) {
        sentence
    } else {
        val cut = sentence.take(TITLE_MAX + 1)
        val space = cut.lastIndexOf(' ').takeIf { it >= TITLE_MAX / 2 } ?: TITLE_MAX
        cut.take(space).trimEnd(',', ';', ':', ' ', '-', '—') + "…"
    }
    return prefix + title
}

/** Чи є в нагадуванні щось понад заголовок — щоб показати повний текст у розгорнутій картці. */
fun hasMoreThanTitle(r: Reminder): Boolean {
    val text = r.text.trim()
    return text.isNotEmpty() && reminderTitle(r).removePrefix("🎤 ").removePrefix("🎬 ").removePrefix("🖼 ") != text
}
