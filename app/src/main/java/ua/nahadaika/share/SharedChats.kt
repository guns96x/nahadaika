package ua.nahadaika.share

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.nahadaika.Prefs
import ua.nahadaika.data.Chat
import ua.nahadaika.data.Comment
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo

/**
 * Спільні чати: поділитися чатом, приєднатися за кодом, тримати нагадування й обговорення
 * в згоді з сервером. Без [SharedBackend] (варіант play, немає налаштувань Firebase) — вимкнено.
 */
@SuppressLint("StaticFieldLeak") // лише applicationContext
object SharedChats {
    private var app: Context? = null
    private var backend: SharedBackend? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watching = mutableMapOf<String, Job>()

    /** Зміни йдуть на сервер строго по черзі: коментар — лише після того, як його нагадування отримало ідентифікатор. */
    private val outgoing = Mutex()

    private fun send(block: suspend () -> Unit) {
        scope.launch { outgoing.withLock { runCatching { block() } } }
    }

    fun available(): Boolean = backend != null

    /** Під'єднати сервер і почати стежити за всіма спільними чатами. */
    fun init(context: Context, backend: SharedBackend, watch: Boolean = true) {
        app = context.applicationContext
        this.backend = backend
        if (watch) scope.launch { runCatching { Repo.sharedChats().forEach { watch(it) } } }
    }

    /** Для тестів: відключити сервер і зупинити стеження. */
    fun reset() {
        watching.values.forEach { it.cancel() }
        watching.clear()
        backend = null
    }

    private suspend fun me(b: SharedBackend): Member {
        val ctx = checkNotNull(app)
        return Member(b.signIn(), Prefs.displayName(ctx).ifBlank { "?" })
    }

    // ---- Дії користувача ----

    /** Зробити чат спільним; повертає код запрошення. Наявні текстові нагадування стають спільними. */
    suspend fun share(chat: Chat): String {
        val b = checkNotNull(backend)
        chat.remoteId?.let { id -> Prefs.inviteCode(checkNotNull(app), id)?.let { return it } }
        val me = me(b)
        val remote = b.createChat(chat.name, me)
        Repo.setChatRemoteId(chat.id, remote.id)
        Prefs.setInviteCode(checkNotNull(app), remote.id, remote.inviteCode)
        Repo.remindersOf(chat.id).filter { it.kind == Kind.TEXT }.forEach { push(b, remote.id, it, me) }
        watch(chat.copy(remoteId = remote.id))
        return remote.inviteCode
    }

    /** Приєднатися за кодом; повертає локальний чат або null, якщо код не знайдено. */
    suspend fun join(code: String): Long? {
        val b = checkNotNull(backend)
        val me = me(b)
        val remote = b.joinChat(code.trim().uppercase(), me) ?: return null
        val chatId = Repo.chatByRemoteId(remote.id)?.id ?: Repo.createSharedChat(remote.name, remote.id)
        Prefs.setInviteCode(checkNotNull(app), remote.id, remote.inviteCode)
        Repo.applyRemote(chatId, b.fetch(remote.id), me.uid)
        Repo.chatById(chatId)?.let { watch(it) }
        return chatId
    }

    fun inviteCode(chat: Chat): String? = chat.remoteId?.let { id -> app?.let { Prefs.inviteCode(it, id) } }

    /** Разова синхронізація всіх спільних чатів (фонова перевірка, коли застосунок закрито). */
    suspend fun syncOnce() {
        val b = backend ?: return
        val uid = b.signIn()
        Repo.sharedChats().forEach { chat -> Repo.applyRemote(chat.id, b.fetch(chat.remoteId!!), uid) }
    }

    private fun watch(chat: Chat) {
        val b = backend ?: return
        val remoteId = chat.remoteId ?: return
        if (watching[remoteId]?.isActive == true) return
        watching[remoteId] = scope.launch {
            runCatching {
                val uid = b.signIn()
                b.observe(remoteId).collect { snapshot ->
                    Repo.chatByRemoteId(remoteId)?.let { Repo.applyRemote(it.id, snapshot, uid) }
                }
            }
        }
    }

    // ---- Локальні зміни → сервер (викликає Repo після дій користувача) ----

    fun onReminderChanged(id: Long) {
        val b = backend ?: return
        send {
            val r = Repo.reminder(id) ?: return@send
            val remoteChat = Repo.chatById(r.chatId)?.remoteId ?: return@send
            if (r.kind != Kind.TEXT) return@send
            push(b, remoteChat, r, me(b))
        }
    }

    fun onReminderDeleted(reminder: Reminder) {
        val b = backend ?: return
        val remoteId = reminder.remoteId ?: return
        send {
            val remoteChat = Repo.chatById(reminder.chatId)?.remoteId ?: return@send
            b.deleteReminder(remoteChat, remoteId)
        }
    }

    fun onCommentAdded(comment: Comment) {
        val b = backend ?: return
        send {
            val r = Repo.reminder(comment.reminderId) ?: return@send
            val remoteChat = Repo.chatById(r.chatId)?.remoteId ?: return@send
            val remoteReminder = r.remoteId ?: return@send
            val me = me(b)
            val id = b.newId()
            Repo.setCommentRemoteId(comment.id, id)
            b.putComment(remoteChat, RemoteComment(id, remoteReminder, comment.text, me.uid, me.name, comment.createdAt))
        }
    }

    /** Вийшли з чату (видалили його в себе) — більше не учасник. */
    fun onChatDeleted(chat: Chat) {
        val b = backend ?: return
        val remoteId = chat.remoteId ?: return
        watching.remove(remoteId)?.cancel()
        send { b.leave(remoteId, me(b)) }
    }

    private suspend fun push(b: SharedBackend, remoteChat: String, r: Reminder, me: Member) {
        val create = r.remoteId == null
        val id = r.remoteId ?: b.newId().also { Repo.setReminderRemoteId(r.id, it) }
        b.putReminder(
            remoteChat,
            RemoteReminder(
                id = id,
                text = r.text,
                triggerAt = r.triggerAt,
                repeat = r.repeat,
                alarm = r.alarm,
                done = r.fired && r.repeat == Repeat.NONE,
                authorUid = me.uid,
                authorName = me.name,
                createdAt = r.createdAt,
            ),
            create,
        )
    }
}
