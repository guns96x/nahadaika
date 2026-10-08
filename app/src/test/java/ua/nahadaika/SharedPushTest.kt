package ua.nahadaika

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.share.Member
import ua.nahadaika.share.RemoteComment
import ua.nahadaika.share.RemoteReminder
import ua.nahadaika.share.SharedChats
import ua.nahadaika.share.SyncQueue
import java.io.File

/** Миттєві сповіщення й «кур'єр» медіа з боку застосунку (сервер — підміна в пам'яті). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPushTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L
    private val backend = FakeBackend()

    @Before fun setUp() {
        Repo.init(app)
        SyncQueue.clear(app)
        Prefs.setDisplayName(app, "Я")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SharedChats.init(app, backend, watch = false)
        SharedChats.appVisible = { false }
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
        assertTrue("не дочекались синхронізації", check())
    }

    private fun shared() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        .filter { it.channelId == Notifier.SHARED_CHANNEL_ID }

    private fun media(name: String) = File(app.filesDir, "test_media").apply { mkdirs() }.resolve(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun writesWakeOtherMembersOnlyInSharedChats() = runBlocking {
        // Особиста копія в хмарі — будити нікого.
        Prefs.setCloud(app, true)
        val personal = Repo.createChat("Моє")
        eventually { Repo.chatById(personal)?.remoteId != null }
        Repo.createReminder(Reminder(chatId = personal, kind = Kind.TEXT, text = "Моє", triggerAt = System.currentTimeMillis() + hour))
        eventually { backend.reminders[Repo.chatById(personal)!!.remoteId!!]?.size == 1 }

        val chat = Repo.createChat("Сім'я")
        Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Було раніше", triggerAt = System.currentTimeMillis() + hour))
        SharedChats.share(Repo.chatById(chat)!!)
        val remote = Repo.chatById(chat)!!.remoteId!!
        eventually { remote in backend.pushChats }
        delay(100)
        // Вивантаження наявного при «поділитися» нікого не будить.
        assertTrue(backend.notified.isEmpty())

        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Хліб", triggerAt = System.currentTimeMillis() + hour))
        eventually { backend.notified.contains(remote to true) }
        Repo.editText(Repo.reminder(id)!!, "Хліб і молоко")
        eventually { backend.notified.contains(remote to false) }
        Repo.addComment(id, "Куплю")
        eventually { backend.notified.count { it == remote to true } == 2 }
        Repo.deleteReminder(Repo.reminder(id)!!)
        eventually { backend.notified.count { it == remote to false } == 2 }
        assertTrue(backend.notified.none { it.first != remote })
    }

    @Test fun temporaryMediaTravelsOnlyToSharedChatsAndIsConfirmed() = runBlocking {
        backend.storageEnabled = true
        backend.temporaryMedia = true
        Prefs.setCloud(app, true)
        val chat = Repo.createChat("Сім'я")
        eventually { Repo.chatById(chat)?.remoteId != null }
        val remote = Repo.chatById(chat)!!.remoteId!!
        Repo.createReminder(Reminder(chatId = chat, kind = Kind.PHOTO, text = "Фото", mediaPath = media("p.jpg").path, triggerAt = System.currentTimeMillis() + hour))
        delay(200)
        // Поки немає з ким ділитися, кур'єр файл не везе.
        assertEquals(0, backend.uploads)

        SharedChats.share(Repo.chatById(chat)!!)
        eventually { backend.uploads == 1 && backend.reminders[remote]?.values?.single()?.mediaRef != null }

        // Інший учасник отримує файл і підтверджує доставку.
        val ref = backend.reminders.getValue(remote).values.single().mediaRef!!
        SharedChats.reset()
        backend.uid = "olia"
        SharedChats.init(app, backend, watch = false)
        SharedChats.appVisible = { false }
        Repo.deleteChat(Repo.chatById(chat)!!)
        backend.members.getValue(remote) += "olia"
        val code = backend.invites.entries.first { it.value == remote }.key
        SharedChats.join(code)
        assertEquals(listOf(ref), backend.received)
    }

    @Test fun refusedMediaIsNotRetriedEverySync() = runBlocking {
        backend.storageEnabled = true
        val chat = Repo.createChat("Сім'я")
        SharedChats.share(Repo.chatById(chat)!!)
        val remote = Repo.chatById(chat)!!.remoteId!!
        backend.refuseUploads = true
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.VIDEO, text = "Довге відео", mediaPath = media("v.mp4").path, triggerAt = System.currentTimeMillis() + hour))
        eventually { SyncQueue.isLocalOnly(app, id) }
        val attempts = backend.uploadAttempts
        SharedChats.syncOnce()
        assertEquals(attempts, backend.uploadAttempts)
        assertTrue(backend.reminders[remote].orEmpty().isEmpty())
    }

    @Test fun newsFromOthersShowsOneNotificationPerChat() = runBlocking {
        val remote = backend.createChat("Дім", Member("olia", "Оля"))
        val chatId = SharedChats.join(remote.inviteCode)!!
        // Те, що вже було в чаті при вході, — не новина.
        assertTrue(shared().isEmpty())

        val now = System.currentTimeMillis()
        backend.putReminder(remote.id, RemoteReminder("r1", "Купити хліб", now + hour, Repeat.NONE, false, false, "olia", "Оля", now), create = true)
        backend.putComment(remote.id, RemoteComment("c1", "r1", "І молоко", "olia", "Оля", now))
        backend.putReminder(remote.id, RemoteReminder("r2", "Моє з іншого телефона", now + hour, Repeat.NONE, false, false, "me", "Я", now), create = true)
        SharedChats.syncChat(remote.id)

        // Нове могло прийти й живим слухачем чату, порціями — сповіщення одне, рядки накопичуються.
        eventually { shared().singleOrNull()?.number == 2 }
        val n = shared().single()
        assertEquals("Дім", n.extras.getString("android.title"))
        assertEquals(2, n.number)
        val lines = n.extras.getCharSequenceArray("android.textLines")!!.map { it.toString() }
        assertTrue(lines.toString(), lines.any { "Оля" in it && "Купити хліб" in it })
        assertTrue(lines.toString(), lines.any { "І молоко" in it })
        assertTrue(lines.none { "Моє з іншого телефона" in it })
        assertEquals(chatId, Repo.chatById(chatId)!!.id)
    }

    @Test fun noNotificationWhileAppIsOnScreen() = runBlocking {
        SharedChats.appVisible = { true }
        val remote = backend.createChat("Дім", Member("olia", "Оля"))
        SharedChats.join(remote.inviteCode)
        backend.putReminder(remote.id, RemoteReminder("r1", "Хліб", System.currentTimeMillis() + hour, Repeat.NONE, false, false, "olia", "Оля", 1), create = true)
        SharedChats.syncChat(remote.id)
        eventually { Repo.remindersOf(Repo.chatByRemoteId(remote.id)!!.id).isNotEmpty() }
        delay(100)
        assertTrue(shared().isEmpty())
        assertEquals(1, Repo.remindersOf(Repo.chatByRemoteId(remote.id)!!.id).size)
    }
}
