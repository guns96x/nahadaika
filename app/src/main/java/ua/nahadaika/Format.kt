package ua.nahadaika

import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val uk: Locale = Locale.forLanguageTag("uk")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", uk)
private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMMM", uk)
private val dayMonthYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", uk)
private val shortDateFmt = DateTimeFormatter.ofPattern("EE, d MMM", uk)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)

/** "Сьогодні", "Завтра", "5 жовтня", "5 жовтня 2027". */
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сьогодні"
        today.plusDays(1) -> "Завтра"
        today.minusDays(1) -> "Вчора"
        else -> date.format(if (date.year == today.year) dayMonthFmt else dayMonthYearFmt)
    }
}

/** "сьогодні о 20:00", "5 жовтня о 09:00". */
fun whenLabel(ms: Long): String {
    val day = dayLabel(ms.toLocalDate()).replaceFirstChar { it.lowercase(uk) }
    return "$day о ${formatTime(ms)}"
}

/** Коротко для списку чатів: "20:00", "завтра", "5 жовт.". */
fun shortWhen(ms: Long): String {
    val date = ms.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> formatTime(ms)
        today.plusDays(1) -> "завтра"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", uk))
    }
}

fun shortDate(date: LocalDate): String = date.format(shortDateFmt)

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

fun repeatLabel(repeat: Repeat): String = when (repeat) {
    Repeat.NONE -> "Не повторювати"
    Repeat.DAILY -> "Щодня"
    Repeat.WEEKLY -> "Щотижня"
    Repeat.MONTHLY -> "Щомісяця"
    Repeat.YEARLY -> "Щороку"
}

/** Опис повідомлення одним рядком — для сповіщень і списку чатів. */
fun previewText(r: Reminder): String {
    val caption = r.text.takeIf { it.isNotBlank() }
    return when (r.kind) {
        Kind.TEXT -> r.text
        Kind.VOICE -> "🎤 Голосове (${formatDuration(r.durationMs)})" + (caption?.let { " · $it" } ?: "")
        Kind.VIDEO -> "🎬 " + (caption ?: "Відео")
        Kind.PHOTO -> "🖼 " + (caption ?: "Фото")
    }
}
