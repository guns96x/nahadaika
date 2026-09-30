package ua.nahadaika

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class RepeatTest {
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(original)

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun dailySkipsToFirstFutureOccurrence() {
        val next = Repo.nextOccurrence(ms(2026, 9, 1), Repeat.DAILY, now = ms(2026, 9, 30, 12))
        assertEquals(ms(2026, 10, 1), next)
    }

    @Test
    fun dailyKeepsLocalTimeAcrossDstChange() {
        // 25.10.2026 Україна переходить на зимовий час.
        val next = Repo.nextOccurrence(ms(2026, 10, 24), Repeat.DAILY, now = ms(2026, 10, 24, 10))
        assertEquals(ms(2026, 10, 25), next)
    }

    @Test
    fun monthlyOn31stDoesNotDrift() {
        val start = ms(2026, 1, 31)
        assertEquals(ms(2026, 2, 28), Repo.nextOccurrence(start, Repeat.MONTHLY, now = ms(2026, 2, 1)))
        assertEquals(ms(2026, 3, 31), Repo.nextOccurrence(start, Repeat.MONTHLY, now = ms(2026, 3, 1)))
    }

    @Test
    fun weeklyAndYearly() {
        assertEquals(ms(2026, 10, 7), Repo.nextOccurrence(ms(2026, 9, 30), Repeat.WEEKLY, now = ms(2026, 9, 30, 10)))
        assertEquals(ms(2027, 3, 8), Repo.nextOccurrence(ms(2026, 3, 8), Repeat.YEARLY, now = ms(2026, 9, 30)))
    }
}
