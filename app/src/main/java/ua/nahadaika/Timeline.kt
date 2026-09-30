package ua.nahadaika

import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.alarmAt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Одне нагадування на таймлайні конкретного дня: коли саме і чи вже надійшло. */
data class Occurrence(val reminder: Reminder, val at: Long, val done: Boolean)

/**
 * Що припадає на [date]: одноразові — у свій день, надіслані — в день надсилання,
 * повторювані — у кожен відповідний день, починаючи з найближчого.
 */
fun occurrencesOn(reminders: List<Reminder>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> =
    reminders.mapNotNull { occurrenceOn(it, date, zone) }.sortedBy { it.at }

fun occurrenceOn(r: Reminder, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Occurrence? {
    fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    if (r.fired) {
        val sent = r.lastFiredAt ?: r.triggerAt
        return if (sent.date() == date) Occurrence(r, sent, done = true) else null
    }

    val next = r.alarmAt()
    val nextDate = next.date()
    if (date == nextDate) return Occurrence(r, next, done = false)

    // Повторюване, яке вже надходило цього дня (напр. «щодня о 8» — сьогоднішнє вже було).
    r.lastFiredAt?.let { if (it.date() == date && date.isBefore(nextDate)) return Occurrence(r, it, done = true) }

    if (r.repeat == Repeat.NONE || date.isBefore(nextDate)) return null
    val base = Instant.ofEpochMilli(r.triggerAt).atZone(zone)
    val day = minOf(base.dayOfMonth, date.lengthOfMonth())
    val matches = when (r.repeat) {
        Repeat.DAILY -> true
        Repeat.WEEKLY -> date.dayOfWeek == base.dayOfWeek
        Repeat.MONTHLY -> date.dayOfMonth == day
        Repeat.YEARLY -> date.month == base.month && date.dayOfMonth == day
        Repeat.NONE -> false
    }
    if (!matches) return null
    return Occurrence(r, date.atTime(base.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), done = false)
}
