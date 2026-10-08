package ua.nahadaika

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import ua.nahadaika.share.Member
import ua.nahadaika.share.RemoteReminder
import ua.nahadaika.share.SharedChats
import ua.nahadaika.share.SyncQueue
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedMediaSyncTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L
    private val backend = FakeBackend()

    @Before fun setUp() {
        Repo.init(app)
        SyncQueue.clear(app)
        Prefs.setDisplayName(app, "Я")
        backend.storageEnabled = true
        SharedChats.init(app, backend, watch = false)
    }

    @After fun tearDown() {
        SyncQueue.clear(app)
        SharedChats.reset()
    }

    private suspend fun eventually(check: suspend () -> Boolean) {
        repeat(100) {
            if (check()) return
            delay(20)
        }
        assertTrue("не дочекались завершення операції", check())
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size

    private fun createTempMedia(prefix: String, ext: String, bytes: ByteArray): File {
        val dir = File(app.filesDir, "test_media").apply { mkdirs() }
        val f = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        f.writeBytes(bytes)
        return f
    }

    @Test fun voiceVideoPhotoSyncWithRealTempBytes() = runBlocking {
        val voiceBytes = byteArrayOf(1, 2, 3, 4, 5)
        val videoBytes = byteArrayOf(10, 20, 30, 40)
        val photoBytes = byteArrayOf(100.toByte(), 101.toByte(), 102.toByte())

        val voiceFile = createTempMedia("voice", "m4a", voiceBytes)
        val videoFile = createTempMedia("circle", "mp4", videoBytes)
        val photoFile = createTempMedia("photo", "jpg", photoBytes)

        val chat = Repo.createChat("Сім'я")
        val vId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.VOICE, text = "Голосове", mediaPath = voiceFile.absolutePath, durationMs = 5000, triggerAt = System.currentTimeMillis() + hour),
        )
        val vidId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.VIDEO, text = "Відео", mediaPath = videoFile.absolutePath, durationMs = 7500, triggerAt = System.currentTimeMillis() + hour),
        )
        val pId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.PHOTO, text = "Фото", mediaPath = photoFile.absolutePath, triggerAt = System.currentTimeMillis() + hour),
        )

        val code = SharedChats.share(Repo.chatById(chat)!!)
        val remoteId = Repo.chatById(chat)!!.remoteId!!

        eventually { backend.reminders[remoteId]?.size == 3 }
        assertEquals(3, backend.mediaStore.size)

        // Перевіряємо завантажені у FakeBackend байти
        val remoteVoice = backend.reminders.getValue(remoteId).values.first { it.kind == Kind.VOICE }
        assertEquals("Голосове", remoteVoice.text)
        assertEquals(5000L, remoteVoice.durationMs)
        assertNotNull(remoteVoice.mediaRef)
        assertArrayEquals(voiceBytes, backend.mediaStore[remoteVoice.mediaRef])

        val remoteVideo = backend.reminders.getValue(remoteId).values.first { it.kind == Kind.VIDEO }
        assertEquals(7500L, remoteVideo.durationMs)
        assertArrayEquals(videoBytes, backend.mediaStore[remoteVideo.mediaRef])

        val remotePhoto = backend.reminders.getValue(remoteId).values.first { it.kind == Kind.PHOTO }
        assertArrayEquals(photoBytes, backend.mediaStore[remotePhoto.mediaRef])

        // Тепер інший учасник (наприклад, Оля) приєднується до чату
        SharedChats.reset()
        val oliaBackend = backend
        oliaBackend.uid = "olia"
        SharedChats.init(app, oliaBackend, watch = false)

        // Очищаємо локальні нагадування, щоб імітувати інший телефон
        Repo.deleteChat(Repo.chatById(chat)!!)

        val joinedChatId = SharedChats.join(code)!!
        val joinedReminders = Repo.reminders(joinedChatId).first().associateBy { it.kind }

        assertEquals(3, joinedReminders.size)

        val dlVoice = joinedReminders.getValue(Kind.VOICE)
        assertNotNull(dlVoice.mediaPath)
        assertTrue(File(dlVoice.mediaPath!!).exists())
        assertArrayEquals(voiceBytes, File(dlVoice.mediaPath!!).readBytes())
        assertEquals(5000L, dlVoice.durationMs)

        val dlVideo = joinedReminders.getValue(Kind.VIDEO)
        assertNotNull(dlVideo.mediaPath)
        assertTrue(File(dlVideo.mediaPath!!).exists())
        assertArrayEquals(videoBytes, File(dlVideo.mediaPath!!).readBytes())
        assertEquals(7500L, dlVideo.durationMs)

        val dlPhoto = joinedReminders.getValue(Kind.PHOTO)
        assertNotNull(dlPhoto.mediaPath)
        assertTrue(File(dlPhoto.mediaPath!!).exists())
        assertArrayEquals(photoBytes, File(dlPhoto.mediaPath!!).readBytes())
    }

    @Test fun textCompatibilityWithOlderDocuments() = runBlocking {
        val remote = backend.createChat("Архів", Member("olia", "Оля"))
        val now = System.currentTimeMillis()
        // Старий документ без полів kind, mediaRef, durationMs
        val oldDoc = RemoteReminder(
            id = "old1",
            text = "Старе текстове",
            triggerAt = now + hour,
            repeat = Repeat.NONE,
            alarm = false,
            done = false,
            authorUid = "olia",
            authorName = "Оля",
            createdAt = 100,
        )
        backend.putReminder(remote.id, oldDoc, create = true)

        val chatId = SharedChats.join(remote.inviteCode)!!
        val local = Repo.reminders(chatId).first().single()

        assertEquals("Старе текстове", local.text)
        assertEquals(Kind.TEXT, local.kind)
        assertNull(local.mediaPath)
        assertEquals(0L, local.durationMs)
    }

    @Test fun noAuthorFileDeletionOnRemoteUpdates() = runBlocking {
        val voiceBytes = byteArrayOf(9, 8, 7, 6)
        val authorFile = createTempMedia("author_voice", "m4a", voiceBytes)

        val chat = Repo.createChat("Дім")
        val rId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.VOICE, text = "Слухати", mediaPath = authorFile.absolutePath, durationMs = 3000, triggerAt = System.currentTimeMillis() + hour),
        )
        val code = SharedChats.share(Repo.chatById(chat)!!)
        val remoteChatId = Repo.chatById(chat)!!.remoteId!!

        eventually { backend.reminders[remoteChatId]?.size == 1 }

        // Оля змінює текст і позначає «виконано» на сервері
        val serverReminder = backend.reminders.getValue(remoteChatId).values.single()
        backend.putReminder(
            remoteChatId,
            serverReminder.copy(text = "Оновлено Олею", done = true),
            create = false,
        )

        SharedChats.syncOnce()

        val updatedLocal = Repo.reminder(rId)!!
        assertEquals("Оновлено Олею", updatedLocal.text)
        assertTrue(updatedLocal.fired)
        // Файл автора НЕ повинен бути видалений чи перезаписаний!
        assertEquals(authorFile.absolutePath, updatedLocal.mediaPath)
        assertTrue("Файл автора існує", authorFile.exists())
        assertArrayEquals(voiceBytes, authorFile.readBytes())
    }

    @Test fun offlineUploadRetryOnSyncOnce() = runBlocking {
        val photoBytes = byteArrayOf(42, 43, 44)
        val photoFile = createTempMedia("offline_photo", "jpg", photoBytes)

        val chat = Repo.createChat("Робота")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChatId = Repo.chatById(chat)!!.remoteId!!

        // Імітуємо збій мережі під час завантаження медіа
        backend.failNextUpload = true

        val rId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.PHOTO, text = "Звіт", mediaPath = photoFile.absolutePath, triggerAt = System.currentTimeMillis() + hour),
        )

        // Чекаємо обробки
        delay(100)

        val localBeforeRetry = Repo.reminder(rId)!!
        // remoteId НЕ повинен бути виставлений передчасно!
        assertNull("remoteId не повинен виставлятися при збої", localBeforeRetry.remoteId)
        // Сервер ще не має цього нагадування
        assertTrue(backend.reminders[remoteChatId].isNullOrEmpty())

        // Імітуємо появу знімка від сервера (без нашого нагадування)
        // applyRemote не повинен видалити створене локальне нагадування
        Repo.applyRemote(chat, backend.fetch(remoteChatId), "me")
        assertNotNull("локальне нагадування не втрачено", Repo.reminder(rId))

        // Мережа відновилася — викликаємо syncOnce
        SharedChats.syncOnce()

        val localAfterRetry = Repo.reminder(rId)!!
        assertNotNull("після syncOnce remoteId виставлено", localAfterRetry.remoteId)
        assertEquals(1, backend.reminders[remoteChatId]?.size)
        assertEquals(1, backend.mediaStore.size)
    }

    @Test fun offlineDeletionDoesNotResurrectOnRemoteSnapshot() = runBlocking {
        val chat = Repo.createChat("Спільне")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChatId = Repo.chatById(chat)!!.remoteId!!

        val rId = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.TEXT, text = "Спільне нагадування", triggerAt = System.currentTimeMillis() + hour),
        )
        eventually { backend.reminders[remoteChatId]?.size == 1 }

        val reminder = Repo.reminder(rId)!!
        assertNotNull(reminder.remoteId)
        val remoteId = reminder.remoteId!!

        // Справжня помилка мережі: чергу має записати сам застосунок.
        backend.failWrites = true
        Repo.deleteReminder(reminder)
        assertNull(Repo.reminder(rId))

        // Знімок із сервера приходить ДО того, як сервер опрацював видалення
        val oldSnapshot = backend.fetch(remoteChatId)
        assertEquals(1, oldSnapshot.reminders.size)

        Repo.applyRemote(chat, oldSnapshot, "me")

        // Нагадування НЕ повинно воскреснути!
        assertNull("нагадування не воскресло", Repo.reminderByRemoteId(remoteId))

        // Після syncOnce видалення застосовується й до сервера
        backend.failWrites = false
        SharedChats.syncOnce()
        assertTrue(backend.reminders[remoteChatId]?.isEmpty() == true)
        assertFalse(SyncQueue.isPendingDeletion(app, remoteChatId, remoteId))
    }

    @Test fun failedEditAndCommentSurviveOldSnapshotAndRetry() = runBlocking {
        val chat = Repo.createChat("Офлайн")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChat = Repo.chatById(chat)!!.remoteId!!
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Старе", triggerAt = System.currentTimeMillis() + hour))
        eventually { Repo.reminder(id)?.remoteId != null && !SyncQueue.isPendingUpdate(app, id) }
        backend.failWrites = true
        val before = backend.writeAttempts
        Repo.editText(Repo.reminder(id)!!, "Нове офлайн")
        Repo.addComment(id, "Не загубити")
        eventually { backend.writeAttempts > before }
        SharedChats.syncOnce()
        assertEquals("Нове офлайн", Repo.reminder(id)!!.text)
        assertTrue(SyncQueue.isPendingUpdate(app, id))
        backend.failWrites = false
        SharedChats.syncOnce()
        assertEquals("Нове офлайн", backend.reminders.getValue(remoteChat).values.single().text)
        assertEquals(1, backend.comments.getValue(remoteChat).size)
        assertEquals(1, Repo.comments(id).first().size)
        assertFalse(SyncQueue.isPendingUpdate(app, id))
    }

    @Test fun offlineDeletionDoesNotResurrectFromSnapshot() = runBlocking {
        val chat = Repo.createChat("Видалення")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChat = Repo.chatById(chat)!!.remoteId!!
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Прибрати", triggerAt = System.currentTimeMillis() + hour))
        eventually { Repo.reminder(id)?.remoteId != null }
        val remoteId = Repo.reminder(id)!!.remoteId!!
        backend.failWrites = true
        Repo.deleteReminder(Repo.reminder(id)!!)
        SharedChats.syncOnce()
        assertTrue(Repo.remindersOf(chat).isEmpty())
        assertTrue(SyncQueue.isPendingDeletion(app, remoteChat, remoteId))
        backend.failWrites = false
        SharedChats.syncOnce()
        assertTrue(backend.reminders.getValue(remoteChat).isEmpty())
        assertFalse(SyncQueue.isPendingDeletion(app, remoteChat, remoteId))
    }

    @Test fun offlineEditOfRemotelyDeletedReminderDoesNotBecomeZombie() = runBlocking {
        val chat = Repo.createChat("Конфлікт")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChat = Repo.chatById(chat)!!.remoteId!!
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Купити хліб", triggerAt = System.currentTimeMillis() + hour))
        eventually { Repo.reminder(id)?.remoteId != null && !SyncQueue.isPendingUpdate(app, id) }
        backend.failWrites = true
        val before = backend.writeAttempts
        Repo.editText(Repo.reminder(id)!!, "Купити два хліби")
        eventually { backend.writeAttempts > before }
        // Тим часом Оля видалила нагадування.
        backend.reminders.getValue(remoteChat).clear()
        backend.failWrites = false
        SharedChats.syncOnce()
        assertNull(Repo.reminder(id))
        assertFalse(SyncQueue.isPendingUpdate(app, id))
        assertTrue(backend.reminders.getValue(remoteChat).isEmpty())
    }

    @Test fun reschedulingMediaDoesNotUploadFileAgain() = runBlocking {
        val voice = createTempMedia("again", "m4a", byteArrayOf(1, 2, 3))
        val chat = Repo.createChat("Перенос")
        val id = Repo.createReminder(
            Reminder(chatId = chat, kind = Kind.VOICE, text = "Голосове", mediaPath = voice.absolutePath, durationMs = 1000, triggerAt = System.currentTimeMillis() + hour),
        )
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChat = Repo.chatById(chat)!!.remoteId!!
        eventually { backend.reminders[remoteChat]?.size == 1 && !SyncQueue.isPendingUpdate(app, id) }
        assertEquals(1, backend.uploads)
        val later = System.currentTimeMillis() + 5 * hour
        Repo.reschedule(Repo.reminder(id)!!, later, Repeat.NONE)
        eventually { backend.reminders.getValue(remoteChat).values.single().triggerAt == later }
        assertEquals(1, backend.uploads)
        assertNotNull(backend.reminders.getValue(remoteChat).values.single().mediaRef)
    }

    @Test fun lostAcknowledgementRetriesSameRemoteIdWithoutDuplicates() = runBlocking {
        val chat = Repo.createChat("Підтвердження")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteChat = Repo.chatById(chat)!!.remoteId!!
        backend.loseNextReminderAcknowledgement = true
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Один запис", triggerAt = System.currentTimeMillis() + hour))
        eventually { backend.reminders[remoteChat]?.size == 1 }
        SharedChats.syncOnce()
        assertEquals(1, backend.reminders.getValue(remoteChat).size)
        assertEquals(1, Repo.remindersOf(chat).size)
        assertNotNull(Repo.reminder(id)?.remoteId)
    }

    @Test fun storageUnavailableBehaviorPreservesTextAndLocalMedia() = runBlocking {
        backend.storageEnabled = false // Storage вимкнено (немає бакета)

        val voiceFile = createTempMedia("local_only", "m4a", byteArrayOf(1, 2, 3))
        val chat = Repo.createChat("Змішаний")

        Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Лише текст", triggerAt = System.currentTimeMillis() + hour))
        val vId = Repo.createReminder(Reminder(chatId = chat, kind = Kind.VOICE, text = "Локальний голос", mediaPath = voiceFile.absolutePath, triggerAt = System.currentTimeMillis() + hour))

        val code = SharedChats.share(Repo.chatById(chat)!!)
        val remoteChatId = Repo.chatById(chat)!!.remoteId!!

        eventually { backend.reminders[remoteChatId]?.size == 1 }

        // Лише текстове потрапило на сервер
        val remoteList = backend.reminders.getValue(remoteChatId).values.toList()
        assertEquals(1, remoteList.size)
        assertEquals("Лише текст", remoteList.single().text)
        assertTrue(backend.mediaStore.isEmpty())

        // Локальне голосове нагадування на місці, файл не видалено
        val localVoice = Repo.reminder(vId)!!
        assertNotNull(localVoice)
        assertTrue(File(localVoice.mediaPath!!).exists())
    }

    @Test fun joiningPastRemindersDoesNotRingExpiredOneOffs() = runBlocking {
        val remote = backend.createChat("Минуле", Member("olia", "Оля"))
        val now = System.currentTimeMillis()
        // Прострочене разове нагадування
        backend.putReminder(remote.id, RemoteReminder(id = "past1", text = "Вчора о 12", triggerAt = now - 24 * hour, repeat = Repeat.NONE, alarm = true, done = false, authorUid = "olia", authorName = "Оля", createdAt = 1), create = true)
        // Майбутнє нагадування
        backend.putReminder(remote.id, RemoteReminder(id = "future1", text = "Завтра о 12", triggerAt = now + 24 * hour, repeat = Repeat.NONE, alarm = true, done = false, authorUid = "olia", authorName = "Оля", createdAt = 2), create = true)

        val alarmsBefore = alarms()
        val chatId = SharedChats.join(remote.inviteCode)!!

        val reminders = Repo.reminders(chatId).first().associateBy { it.remoteId }
        assertTrue("минуле разове одразу позначене fired", reminders.getValue("past1").fired)
        assertFalse("майбутнє не fired", reminders.getValue("future1").fired)

        // Будильник додано ТІЛЬКИ для майбутнього (+1, а не +2)
        assertEquals(alarmsBefore + 1, alarms())
    }
}
