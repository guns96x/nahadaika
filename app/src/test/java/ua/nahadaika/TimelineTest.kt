package ua.nahadaika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineTest {
    private val zone = ZoneId.of("Europe/Kyiv")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun reminder(at: Long, repeat: Repeat = Repeat.NONE, fired: Boolean = false, lastFiredAt: Long? = null) =
        Reminder(id = at, chatId = 1, kind = Kind.TEXT, text = "x", triggerAt = at, repeat = repeat, fired = fired, lastFiredAt = lastFiredAt)

    private val wed = LocalDate.of(2026, 9, 30)

    @Test fun oneOffOnItsDayOnly() {
        val r = reminder(ms(2026, 9, 30, 18, 30))
        assertEquals(ms(2026, 9, 30, 18, 30), occurrenceOn(r, wed, zone)!!.at)
        assertNull(occurrenceOn(r, wed.plusDays(1), zone))
    }

    @Test fun firedShownOnSendDayAsDone() {
        val r = reminder(ms(2026, 9, 29, 9), fired = true, lastFiredAt = ms(2026, 9, 29, 9, 1))
        val o = occurrenceOn(r, wed.minusDays(1), zone)!!
        assertTrue(o.done)
        assertNull(occurrenceOn(r, wed, zone))
    }

    @Test fun dailyAppearsEveryDayFromNext() {
        val r = reminder(ms(2026, 10, 1, 8), Repeat.DAILY, lastFiredAt = ms(2026, 9, 30, 8))
        assertTrue(occurrenceOn(r, wed, zone)!!.done) // сьогоднішнє вже було
        assertEquals(ms(2026, 10, 5, 8), occurrenceOn(r, LocalDate.of(2026, 10, 5), zone)!!.at)
        assertNull(occurrenceOn(r, wed.minusDays(3), zone))
    }

    @Test fun weeklyOnlyOnSameWeekday() {
        val r = reminder(ms(2026, 10, 5, 10), Repeat.WEEKLY) // понеділок
        assertEquals(ms(2026, 10, 12, 10), occurrenceOn(r, LocalDate.of(2026, 10, 12), zone)!!.at)
        assertNull(occurrenceOn(r, LocalDate.of(2026, 10, 13), zone))
    }

    @Test fun monthlyOn31stFallsOnLastDay() {
        val r = reminder(ms(2026, 10, 31, 9), Repeat.MONTHLY)
        assertEquals(ms(2026, 11, 30, 9), occurrenceOn(r, LocalDate.of(2026, 11, 30), zone)!!.at)
        assertNull(occurrenceOn(r, LocalDate.of(2026, 11, 29), zone))
    }

    @Test fun dayListIsSortedByTime() {
        val list = listOf(reminder(ms(2026, 9, 30, 20)), reminder(ms(2026, 9, 30, 7)), reminder(ms(2026, 10, 1, 9)))
        assertEquals(listOf(ms(2026, 9, 30, 7), ms(2026, 9, 30, 20)), occurrencesOn(list, wed, zone).map { it.at })
    }
}
