package ua.nahadaika

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import ua.nahadaika.share.ChatSnapshot
import ua.nahadaika.share.Member
import ua.nahadaika.share.RemoteChat
import ua.nahadaika.share.RemoteComment
import ua.nahadaika.share.RemoteReminder
import ua.nahadaika.share.SharedBackend
import ua.nahadaika.share.SharedChats
import java.util.UUID

/** Сервер спільних чатів у пам'яті — як Firestore, але без мережі. */
class FakeBackend(var uid: String = "me") : SharedBackend {
    val chats = mutableMapOf<String, String>() // id → назва
    val invites = mutableMapOf<String, String>() // код → id чату
    val members = mutableMapOf<String, MutableSet<String>>()
    val reminders = mutableMapOf<String, MutableMap<String, RemoteReminder>>()
    val comments = mutableMapOf<String, MutableMap<String, RemoteComment>>()
    private val changes = MutableStateFlow(0)

    override suspend fun signIn() = uid
    override fun newId() = UUID.randomUUID().toString()

    override suspend fun createChat(name: String, me: Member): RemoteChat {
        val id = newId()
        val code = "AB${chats.size}CDE"
        chats[id] = name
        invites[code] = id
        members[id] = mutableSetOf(me.uid)
        return RemoteChat(id, name, code)
    }

    override suspend fun joinChat(code: String, me: Member): RemoteChat? {
        val id = invites[code] ?: return null
        members.getValue(id) += me.uid
        return RemoteChat(id, chats.getValue(id), code)
    }

    override suspend fun fetch(chatId: String) = ChatSnapshot(
        reminders[chatId].orEmpty().values.toList(),
        comments[chatId].orEmpty().values.toList(),
    )

    override fun observe(chatId: String): Flow<ChatSnapshot> = changes.map { fetch(chatId) }

    override suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean) {
        val map = reminders.getOrPut(chatId) { mutableMapOf() }
        val old = map[reminder.id]
        map[reminder.id] = if (create || old == null) reminder else reminder.copy(authorUid = old.authorUid, authorName = old.authorName, createdAt = old.createdAt)
        changes.value++
    }

    override suspend fun deleteReminder(chatId: String, reminderId: String) {
        reminders[chatId]?.remove(reminderId)
        changes.value++
    }

    override suspend fun putComment(chatId: String, comment: RemoteComment) {
        comments.getOrPut(chatId) { mutableMapOf() }[comment.id] = comment
        changes.value++
    }

    override suspend fun leave(chatId: String, me: Member) {
        members[chatId]?.remove(me.uid)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedChatsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L
    private val backend = FakeBackend()

    @Before fun setUp() {
        Repo.init(app)
        Prefs.setDisplayName(app, "Я")
        SharedChats.init(app, backend, watch = false)
    }

    @After fun tearDown() = SharedChats.reset()

    /** Зміни йдуть на сервер у фоні — дочекатися, поки [check] справдиться. */
    private suspend fun eventually(check: () -> Boolean) {
        repeat(100) {
            if (check()) return
            delay(20)
        }
        assertTrue("не дочекались синхронізації", check())
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.size

    private fun remote(id: String, text: String, at: Long, by: String = "olia", name: String = "Оля", repeat: Repeat = Repeat.NONE, done: Boolean = false) =
        RemoteReminder(id, text, at, repeat, alarm = false, done = done, authorUid = by, authorName = name, createdAt = 1)

    @Test fun sharingUploadsTextRemindersOnly() = runBlocking {
        val chat = Repo.createChat("Сім'я")
        Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Хліб", triggerAt = System.currentTimeMillis() + hour))
        Repo.createReminder(Reminder(chatId = chat, kind = Kind.VOICE, mediaPath = "/v.m4a", triggerAt = System.currentTimeMillis() + hour))

        val code = SharedChats.share(Repo.chatById(chat)!!)
        val remoteId = Repo.chatById(chat)!!.remoteId!!
        assertEquals(remoteId, backend.invites[code])
        eventually { backend.reminders[remoteId]?.size == 1 }
        with(backend.reminders.getValue(remoteId).values.single()) {
            assertEquals("Хліб", text)
            assertEquals("me", authorUid)
            assertEquals("Я", authorName)
        }
        // Повторне «поділитися» не створює новий чат — той самий код.
        assertEquals(code, SharedChats.share(Repo.chatById(chat)!!))
    }

    @Test fun newLocalChangesGoToServer() = runBlocking {
        val chat = Repo.createChat("Сім'я")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteId = Repo.chatById(chat)!!.remoteId!!

        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Аптека", triggerAt = System.currentTimeMillis() + hour))
        eventually { backend.reminders[remoteId]?.values?.any { it.text == "Аптека" } == true }
        Repo.editText(Repo.reminder(id)!!, "Аптека й хліб")
        eventually { backend.reminders.getValue(remoteId).values.single().text == "Аптека й хліб" }
        Repo.markDone(id)
        eventually { backend.reminders.getValue(remoteId).values.single().done }
        Repo.addComment(id, "Куплю дорогою")
        eventually { backend.comments[remoteId]?.values?.singleOrNull()?.text == "Куплю дорогою" }

        Repo.deleteReminder(Repo.reminder(id)!!)
        eventually { backend.reminders.getValue(remoteId).isEmpty() }
    }

    @Test fun joiningByCodeBringsChatWithoutRingingHistory() = runBlocking {
        val remote = backend.createChat("Дім", Member("olia", "Оля"))
        val now = System.currentTimeMillis()
        backend.putReminder(remote.id, remote("r1", "Вчорашнє", now - 24 * hour), create = true)
        backend.putReminder(remote.id, remote("r2", "Завтра", now + 24 * hour), create = true)
        backend.putReminder(remote.id, remote("r3", "Вітаміни", now - 2 * hour, repeat = Repeat.DAILY), create = true)
        val alarmsBefore = alarms()

        assertNull(SharedChats.join("NOPE"))
        val chatId = SharedChats.join(remote.inviteCode.lowercase())!!

        val local = Repo.reminders(chatId).first().associateBy { it.text }
        assertEquals("Дім", Repo.chatById(chatId)!!.name)
        assertTrue("вчорашнє — в історії", local.getValue("Вчорашнє").fired)
        assertFalse(local.getValue("Завтра").fired)
        assertEquals("Оля", local.getValue("Завтра").authorName)
        val daily = local.getValue("Вітаміни")
        assertTrue("повторюване — на найближчий раз", daily.triggerAt > now)
        assertEquals("будильники лише на майбутнє", alarmsBefore + 2, alarms())
        assertTrue("me" in backend.members.getValue(remote.id))
    }

    @Test fun remoteChangesUpdateAndRemoveLocalCopies() = runBlocking {
        val remote = backend.createChat("Дім", Member("olia", "Оля"))
        val now = System.currentTimeMillis()
        backend.putReminder(remote.id, remote("r1", "Хліб", now + hour), create = true)
        backend.putReminder(remote.id, remote("r2", "Сир", now + hour), create = true)
        val chatId = SharedChats.join(remote.inviteCode)!!

        // Оля переписала, перенесла, позначила «готово» й видалила.
        backend.putReminder(remote.id, remote("r1", "Хліб і молоко", now + 3 * hour), create = false)
        backend.putReminder(remote.id, remote("r2", "Сир", now + hour, done = true), create = false)
        backend.putComment(remote.id, RemoteComment("c1", "r1", "Візьми чек", "olia", "Оля", now))
        SharedChats.syncOnce()

        var local = Repo.reminders(chatId).first().associateBy { it.remoteId }
        assertEquals("Хліб і молоко", local.getValue("r1").text)
        assertEquals(now + 3 * hour, local.getValue("r1").triggerAt)
        assertTrue(local.getValue("r2").fired)
        val comments = Repo.comments(local.getValue("r1").id).first()
        assertEquals("Оля", comments.single().authorName)

        // Повторна синхронізація нічого не дублює.
        SharedChats.syncOnce()
        assertEquals(1, Repo.comments(local.getValue("r1").id).first().size)

        backend.deleteReminder(remote.id, "r2")
        SharedChats.syncOnce()
        local = Repo.reminders(chatId).first().associateBy { it.remoteId }
        assertNull(local["r2"])
        assertNotNull(local["r1"])
    }

    @Test fun ownEchoDoesNotDuplicate() = runBlocking {
        val chat = Repo.createChat("Сім'я")
        SharedChats.share(Repo.chatById(chat)!!)
        val remoteId = Repo.chatById(chat)!!.remoteId!!
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Хліб", triggerAt = System.currentTimeMillis() + hour))
        Repo.addComment(id, "Мій коментар")
        eventually { backend.comments[remoteId]?.size == 1 && backend.reminders[remoteId]?.size == 1 }

        SharedChats.syncOnce()
        val all = Repo.reminders(chat).first()
        assertEquals(1, all.size)
        assertNull("моє — без підпису автора", all.single().authorName)
        assertEquals(1, Repo.comments(id).first().size)
    }
}
