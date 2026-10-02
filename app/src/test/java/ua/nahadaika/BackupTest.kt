package ua.nahadaika

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.data.BackupFormatException
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.media.MediaFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Repo.init(app)
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private suspend fun seed(): Long {
        val chat = Repo.createChat("Дім")
        val now = System.currentTimeMillis()
        val bread = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Купити хліб", triggerAt = now + hour))
        Repo.addComment(bread, "Беру на себе")
        Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.TEXT, text = "Вітаміни", triggerAt = now + 2 * hour, repeat = Repeat.DAILY, alarm = true),
        )
        val media = MediaFiles.newFile(app, "m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.VOICE, text = "Голос", mediaPath = media.absolutePath, durationMs = 4200, triggerAt = now + 3 * hour),
        )
        Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.TEXT, text = "Було", triggerAt = now - hour, fired = true, lastFiredAt = now - hour + 5),
        )
        return chat
    }

    private fun export(): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { Repo.exportBackup(out) } }.toByteArray()

    private suspend fun wipe() = Repo.chats.first().forEach { Repo.deleteChat(it) }

    @Test
    fun roundTripRestoresChatsRemindersMediaAndAlarms() = runBlocking {
        seed()
        val backup = export()
        wipe()
        assertTrue(Repo.allReminders.first().isEmpty())

        val result = Repo.importBackup(ByteArrayInputStream(backup))
        assertEquals(1, result.chats)
        assertEquals(4, result.reminders)
        assertEquals(0, result.skipped)

        val chat = Repo.chats.first().single()
        assertEquals("Дім", chat.name)
        val restored = Repo.reminders(chat.id).first().associateBy { it.text }
        assertEquals(setOf("Купити хліб", "Вітаміни", "Голос", "Було"), restored.keys)
        assertEquals(Repeat.DAILY, restored.getValue("Вітаміни").repeat)
        assertTrue(restored.getValue("Вітаміни").alarm)
        assertTrue(restored.getValue("Було").fired)
        assertEquals(listOf("Беру на себе"), Repo.comments(restored.getValue("Купити хліб").id).first().map { it.text })
        val voice = restored.getValue("Голос")
        assertEquals(4200L, voice.durationMs)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), File(voice.mediaPath!!).readBytes().toList())
        assertEquals("три майбутні нагадування мають будильники", 3, alarms().size)
    }

    @Test
    fun importingTwiceAddsNothing() = runBlocking {
        seed()
        val backup = export()
        val result = Repo.importBackup(ByteArrayInputStream(backup))
        assertEquals(0, result.chats)
        assertEquals(0, result.reminders)
        assertEquals(4, result.skipped)
        assertEquals(4, Repo.allReminders.first().size)
    }

    @Test
    fun overdueReminderFromBackupIsQuietHistory() = runBlocking {
        val chat = Repo.createChat("Дім")
        Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Давно", triggerAt = System.currentTimeMillis() + 60_000))
        val backup = export()
        wipe()
        // Копію відновлюють, коли час уже минув.
        val past = String(backup.unzipData()).replace(Regex("\"triggerAt\":\\d+"), "\"triggerAt\":1000")
        Repo.importBackup(ByteArrayInputStream(zipOf("data.json" to past.toByteArray())))

        val r = Repo.allReminders.first().single()
        assertTrue(r.fired)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun foreignFilesAreRejected() = runBlocking {
        val bad = listOf(
            "просто текст".toByteArray(),
            zipOf("readme.txt" to "hi".toByteArray()),
            zipOf("data.json" to "{ це не json".toByteArray()),
            zipOf("data.json" to """{"app":"other","format":1}""".toByteArray()),
            zipOf("data.json" to """{"app":"nahadaika","format":99,"chats":[],"reminders":[]}""".toByteArray()),
        )
        bad.forEach { bytes ->
            val failure = runCatching { Repo.importBackup(ByteArrayInputStream(bytes)) }.exceptionOrNull()
            assertTrue("очікувалась BackupFormatException, а було $failure", failure is BackupFormatException)
        }
        assertTrue(Repo.allReminders.first().isEmpty())
    }

    @Test
    fun archiveCannotWriteOutsideItsFolder() = runBlocking {
        val data = """{"app":"nahadaika","format":1,"chats":[],"reminders":[]}"""
        Repo.importBackup(
            ByteArrayInputStream(zipOf("data.json" to data.toByteArray(), "media/../../evil.txt" to "x".toByteArray())),
        )
        assertFalse(File(app.cacheDir.parentFile, "evil.txt").exists())
        assertFalse(File(app.cacheDir, "evil.txt").exists())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ByteArray.unzipData(): ByteArray =
        java.util.zip.ZipInputStream(ByteArrayInputStream(this)).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "data.json" }.let { zip.readBytes() }
        }
}
