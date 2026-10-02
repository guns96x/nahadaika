package ua.nahadaika

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.AppDatabase
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val now = LocalDateTime.of(2026, 9, 30, 10, 0)

    @Test fun alarmAndTimerPhrasesRing() {
        assertTrue(VoiceParser.parse("постав будильник на 7 ранку", now).alarm)
        assertTrue(VoiceParser.parse("таймер на 10 хвилин", now).alarm)
        assertTrue(VoiceParser.parse("розбуди мене о 6", now).alarm)
        assertFalse(VoiceParser.parse("нагадай завтра о 9 купити хліб", now).alarm)
    }

    @Test fun alarmNotificationRingsUntilStopped() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.show(app, Reminder(id = 5, chatId = 1, kind = Kind.TEXT, text = "Будильник", triggerAt = 0, alarm = true), "Дім")
        val nm = app.getSystemService(NotificationManager::class.java)
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(Notifier.ALARM_CHANNEL_ID, n.channelId)
        assertTrue(n.flags and Notification.FLAG_INSISTENT != 0)
        assertNotNull(n.fullScreenIntent)
        assertEquals(listOf("Ще 5 хв", "Вимкнути"), n.actions.map { it.title.toString() })
    }

    @Test fun regularReminderStaysQuiet() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifier.show(app, Reminder(id = 6, chatId = 1, kind = Kind.TEXT, text = "Хліб", triggerAt = 0), "Дім")
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(Notifier.CHANNEL_ID, n.channelId)
        assertTrue(n.flags and Notification.FLAG_INSISTENT == 0)
    }

    /** Нагадування з версій до 3.7 переживають оновлення бази. */
    @Test fun migratesVersion1Database() {
        val file = app.getDatabasePath("nahadaika.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `chats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chatId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `mediaPath` TEXT, `durationMs` INTEGER NOT NULL, `triggerAt` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `snoozedUntil` INTEGER, `fired` INTEGER NOT NULL, `lastFiredAt` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_chatId` ON `reminders` (`chatId`)")
            // Службова таблиця Room версії 1 — інакше Room вважатиме базу чужою.
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO chats VALUES (1, 'Дім', 0, 0)")
            db.execSQL("INSERT INTO reminders VALUES (1, 1, 'TEXT', 'Хліб', NULL, 0, 123, 'NONE', NULL, 0, NULL, 0)")
            db.version = 1
        }
        val room = AppDatabase.create(app)
        // Хеш схеми версії 1 невідомий тесту — дозволяємо Room прийняти базу як є, перевіряємо лише міграцію.
        val r = runBlocking { room.reminders().get(1) }
        // Версія 3: обговорення під нагадуваннями.
        val comments = runBlocking {
            room.comments().insert(ua.nahadaika.data.Comment(reminderId = 1, text = "Ок"))
            room.comments().byReminder(1)
        }
        room.close()
        assertEquals("Хліб", r!!.text)
        assertFalse(r.alarm)
        assertEquals(0L, r.commentsReadAt)
        assertEquals(listOf("Ок"), comments.map { it.text })
    }
}
