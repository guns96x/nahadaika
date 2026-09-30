package ua.nahadaika

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderFlowTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Repo.init(app)
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
    private suspend fun reminder(chatId: Long) = Repo.reminders(chatId).first().single()

    @Test
    fun newReminderSetsExactAlarmWithoutNotifying() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val at = System.currentTimeMillis() + hour
        Repo.createReminder(Reminder(chatId = chatId, kind = Kind.TEXT, text = "Купити хліб", triggerAt = at))

        assertEquals(at, alarms().single().triggerAtTime)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun oneOffReminderNotifiesAndMovesToHistory() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val id = Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Купити хліб", triggerAt = System.currentTimeMillis() - 1000),
        )
        Repo.deliver(id)

        val n = notifications().single()
        assertEquals("Дім", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Купити хліб", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("+10 хв", "+1 год", "Готово"), n.actions.map { it.title.toString() })
        assertTrue(reminder(chatId).fired)
        assertNotNull(reminder(chatId).lastFiredAt)
    }

    @Test
    fun deliveryIsNotDuplicated() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val id = Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Раз", triggerAt = System.currentTimeMillis() - 1000),
        )
        Repo.deliver(id)
        app.getSystemService(NotificationManager::class.java).cancelAll()
        Repo.deliver(id) // напр. повторний будильник або rescheduleAll одночасно
        Repo.rescheduleAll()

        assertTrue(notifications().isEmpty())
    }

    @Test
    fun earlyAlarmDoesNotFire() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val id = Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Потім", triggerAt = System.currentTimeMillis() + hour),
        )
        Repo.deliver(id)

        assertTrue(notifications().isEmpty())
        assertFalse(reminder(chatId).fired)
    }

    @Test
    fun dailyReminderMovesToNextDay() = runBlocking {
        val chatId = Repo.createChat("Ліки")
        val first = System.currentTimeMillis() - 1000
        val id = Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Вітаміни", triggerAt = first, repeat = Repeat.DAILY),
        )
        Repo.deliver(id)

        val expected = Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli()
        val r = reminder(chatId)
        assertFalse(r.fired)
        assertEquals(expected, r.triggerAt)
        assertEquals(expected, alarms().single().triggerAtTime)
        assertEquals(1, notifications().size)
    }

    @Test
    fun snoozeReschedulesAndClearsNotification() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val id = Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Подзвонити", triggerAt = System.currentTimeMillis() - 1000),
        )
        Repo.deliver(id)
        val before = System.currentTimeMillis()
        Repo.snooze(id, 10)

        val r = reminder(chatId)
        assertFalse(r.fired)
        val snoozed = r.snoozedUntil!!
        assertTrue(snoozed >= before + 10 * 60_000 && snoozed < before + 11 * 60_000)
        assertEquals(snoozed, alarms().single().triggerAtTime)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun missedReminderIsDeliveredOnReschedule() = runBlocking {
        val chatId = Repo.createChat("Дім")
        Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.TEXT, text = "Поки телефон був вимкнений", triggerAt = System.currentTimeMillis() - hour),
        )
        Repo.rescheduleAll()

        assertEquals(1, notifications().size)
        assertTrue(reminder(chatId).fired)
    }

    @Test
    fun voiceReminderOffersListenAction() = runBlocking {
        val chatId = Repo.createChat("Дім")
        val id = Repo.createReminder(
            Reminder(
                chatId = chatId, kind = Kind.VOICE, mediaPath = "/nope.m4a", durationMs = 12_000,
                triggerAt = System.currentTimeMillis() - 1000,
            ),
        )
        Repo.deliver(id)

        val n = notifications().single()
        assertEquals("🎤 Голосове (0:12)", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("▶ Слухати", n.actions.last().title.toString())
    }

    @Test
    fun deletingChatRemovesMediaAndAlarms() = runBlocking {
        val chatId = Repo.createChat("Тимчасовий")
        val media = File(app.filesDir, "media/test.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        Repo.createReminder(
            Reminder(chatId = chatId, kind = Kind.PHOTO, mediaPath = media.absolutePath, triggerAt = System.currentTimeMillis() + hour),
        )
        val chat = Repo.chats.first().single { it.id == chatId }
        Repo.deleteChat(chat)

        assertFalse(media.exists())
        assertTrue(alarms().isEmpty())
        assertTrue(Repo.reminders(chatId).first().isEmpty())
    }
}

