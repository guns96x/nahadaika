package ua.nahadaika

import android.app.AlarmManager
import android.app.Application
import android.content.Context
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

/** Сервер чатів у пам'яті — як Firestore, але без мережі. */
class FakeBackend(var uid: String = "me") : SharedBackend {
    var failWrites = false
    var loseNextReminderAcknowledgement = false
    var writeAttempts = 0
    val chats = mutableMapOf<String, String>() // id → назва
    val invites = mutableMapOf<String, String>() // код → id чату
    val members = mutableMapOf<String, MutableSet<String>>()
    val reminders = mutableMapOf<String, MutableMap<String, RemoteReminder>>()
    val comments = mutableMapOf<String, MutableMap<String, RemoteComment>>()
    private val changes = MutableStateFlow(0)

    /** Google-акаунт уже є з іншого телефона — після входу ідентифікатор стає цим. null — анонімний прив'язується. */
    var accountUid: String? = null
    var email: String? = null
    var storageEnabled: Boolean = false
    val mediaStore = mutableMapOf<String, ByteArray>()
    var failNextUpload: Boolean = false
    var uploads = 0

    override suspend fun signIn() = uid
    override suspend fun signInWithGoogle(activity: Context): String {
        accountUid?.let { uid = it }
        email = "me@gmail.com"
        return email!!
    }
    override fun account() = email
    override fun canUseGoogle() = true
    override fun storageAvailable() = storageEnabled
    var temporaryMedia = false
    var refuseUploads = false
    var uploadAttempts = 0
    val notified = mutableListOf<Pair<String, Boolean>>() // чат → терміново
    val received = mutableListOf<String>()
    val pushChats = mutableSetOf<String>()
    override fun mediaIsTemporary() = temporaryMedia
    override suspend fun mediaReceived(chatId: String, mediaRef: String) { received += mediaRef }
    override suspend fun registerPush(chatId: String) { pushChats += chatId }
    val mailbox = mutableMapOf<String, Pair<String, ua.nahadaika.share.MailInvite>>() // id → (пошта, запрошення)
    override suspend fun inviteByEmail(chatId: String, chatName: String, code: String, email: String, fromName: String) {
        val id = "${chatId}_$email"
        mailbox[id] = email to ua.nahadaika.share.MailInvite(id, chatId, chatName, code, fromName)
    }
    override suspend fun myMailInvites() = mailbox.values.filter { it.first == account()?.lowercase() }.map { it.second }
    override suspend fun dismissMailInvite(id: String) { mailbox.remove(id) }
    var stale: List<String> = emptyList()
    var staleChecks = 0
    var failStale = false
    override suspend fun staleMembers(chatId: String): List<String> {
        staleChecks++
        if (failStale) throw java.io.IOException("Мережевий збій")
        return stale
    }
    override suspend fun notifyMembers(chatId: String, urgent: Boolean) { synchronized(notified) { notified += chatId to urgent } }
    override suspend fun uploadMedia(chatId: String, reminderId: String, file: java.io.File, mime: String): ua.nahadaika.share.UploadedMedia? {
        if (!storageEnabled) return null
        uploadAttempts++
        if (refuseUploads) return null
        if (failNextUpload) {
            failNextUpload = false
            throw java.io.IOException("Мережевий збій завантаження медіа")
        }
        uploads++
        val ext = file.extension.ifEmpty { "bin" }
        val ref = "chats/$chatId/media/$reminderId.$ext"
        mediaStore[ref] = file.readBytes()
        return ua.nahadaika.share.UploadedMedia(ref, file.length(), mime)
    }
    override suspend fun downloadMedia(mediaRef: String, target: java.io.File): Boolean {
        if (!storageEnabled) return false
        val bytes = mediaStore[mediaRef] ?: return false
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return true
    }
    override fun newId() = UUID.randomUUID().toString()

    private fun codeOf(chatId: String) = invites.entries.first { it.value == chatId }.key

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
        return RemoteChat(id, chats.getValue(id), code, members.getValue(id).size)
    }

    override suspend fun myChats() = members.filterValues { uid in it }.keys
        .filter { it in chats }
        .map { RemoteChat(it, chats.getValue(it), codeOf(it), members.getValue(it).size) }

    override suspend fun renameChat(chatId: String, name: String) {
        chats[chatId] = name
        changes.value++
    }

    override suspend fun fetch(chatId: String) = ChatSnapshot(
        reminders[chatId].orEmpty().values.toList(),
        comments[chatId].orEmpty().values.toList(),
        chats[chatId],
    )

    override fun observe(chatId: String): Flow<ChatSnapshot> = changes.map { fetch(chatId) }

    override suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean) {
        writeAttempts++
        if (failWrites) error("offline")
        val map = reminders.getOrPut(chatId) { mutableMapOf() }
        val old = map[reminder.id]
        if (!create && old == null) throw ua.nahadaika.share.RemoteGone()
        // Як update у Firestore: поля, яких немає в запиті (автор, медіа), лишаються.
        map[reminder.id] = if (create || old == null) reminder else reminder.copy(
            authorUid = old.authorUid, authorName = old.authorName, createdAt = old.createdAt,
            mediaRef = reminder.mediaRef ?: old.mediaRef,
            mediaSize = if (reminder.mediaRef != null) reminder.mediaSize else old.mediaSize,
            mediaMime = reminder.mediaMime ?: old.mediaMime,
        )
        changes.value++
        if (loseNextReminderAcknowledgement) {
            loseNextReminderAcknowledgement = false
            error("lost acknowledgement")
        }
    }

    override suspend fun deleteReminder(chatId: String, reminderId: String) {
        if (failWrites) error("offline")
        reminders[chatId]?.remove(reminderId)
        changes.value++
    }

    override suspend fun putComment(chatId: String, comment: RemoteComment) {
        if (failWrites) error("offline")
        comments.getOrPut(chatId) { mutableMapOf() }[comment.id] = comment
        changes.value++
    }

    override suspend fun leave(chatId: String, me: Member) {
        val m = members[chatId] ?: return
        m.remove(me.uid)
        if (m.isEmpty()) chats.remove(chatId)
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
        ua.nahadaika.share.SyncQueue.clear(app)
        Prefs.setDisplayName(app, "Я")
        SharedChats.init(app, backend, watch = false)
    }

    @After fun tearDown() {
        ua.nahadaika.share.SyncQueue.clear(app)
        SharedChats.reset()
    }

    /** Зміни йдуть на сервер у фоні — дочекатися, поки [check] справдиться. */
    private suspend fun eventually(check: suspend () -> Boolean) {
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

    // ---- Хмара для всіх чатів ----

    @Test fun cloudRestoresChatsFromAnotherPhoneAndUploadsLocalOnes() = runBlocking {
        // На іншому телефоні цього Google-акаунта вже є два чати.
        backend.accountUid = "acc"
        val home = backend.createChat(Res.s(R.string.core_default_chat_name), Member("acc", "Я"))
        backend.putReminder(home.id, remote("h1", "Оплатити світло", System.currentTimeMillis() + hour, by = "acc", name = "Я"), create = true)
        backend.createChat("Робота", Member("acc", "Я"))
        // Тут — порожній чат за замовчуванням і свій чат із нагадуванням.
        Repo.ensureDefaultChat()
        val dacha = Repo.createChat("Дача")
        Repo.createReminder(Reminder(chatId = dacha, kind = Kind.TEXT, text = "Полити", triggerAt = System.currentTimeMillis() + hour))

        assertEquals("me@gmail.com", SharedChats.enableCloud(app))
        assertTrue(SharedChats.cloudEnabled())

        val chats = Repo.allChats()
        assertEquals("порожній чат за замовчуванням не дублюється", 1, chats.count { it.name == Res.s(R.string.core_default_chat_name) })
        assertEquals(setOf(Res.s(R.string.core_default_chat_name), "Робота", "Дача"), chats.map { it.name }.toSet())
        assertTrue("усі чати в хмарі", chats.all { it.remoteId != null })
        assertTrue("це копії, а не спільні чати", chats.none { SharedChats.isShared(it) })
        val restored = Repo.remindersOf(chats.first { it.remoteId == home.id }.id).single()
        assertEquals("Оплатити світло", restored.text)
        assertNull("моє — без підпису автора", restored.authorName)
        val dachaRemote = Repo.chatById(dacha)!!.remoteId!!
        eventually { backend.reminders[dachaRemote]?.values?.singleOrNull()?.text == "Полити" }
        assertTrue("acc" in backend.members.getValue(dachaRemote))
    }

    @Test fun withCloudNewChatsAndRenamesGoUp() = runBlocking {
        SharedChats.enableCloud(app)
        val sport = Repo.createChat("Спорт")
        eventually { Repo.chatById(sport)!!.remoteId != null }
        val remoteId = Repo.chatById(sport)!!.remoteId!!
        assertEquals("Спорт", backend.chats[remoteId])

        Repo.renameChat(Repo.chatById(sport)!!, "Спортзал")
        eventually { backend.chats[remoteId] == "Спортзал" }

        // Перейменували на іншому телефоні — назва приходить сюди.
        backend.renameChat(remoteId, "Басейн")
        SharedChats.syncOnce()
        assertEquals("Басейн", Repo.chatById(sport)!!.name)

        // Видалити особистий чат — прибрати його й із хмари.
        Repo.deleteChat(Repo.chatById(sport)!!)
        eventually { remoteId !in backend.chats }
    }

    @Test fun withoutCloudChatsStayOnPhone() = runBlocking {
        val id = Repo.createChat("Лише тут")
        delay(200)
        assertNull(Repo.chatById(id)!!.remoteId)
        assertTrue(backend.chats.isEmpty())
    }
}
