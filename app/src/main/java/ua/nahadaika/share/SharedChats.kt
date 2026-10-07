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
 * Чати в хмарі: спільні (поділитися, приєднатися за кодом) і особисті копії всіх чатів, якщо ввімкнено
 * «Зберігати в хмарі». Головні дані — на телефоні (Room): будильники, віджет і робота без інтернету;
 * сервер лише зберігає копію й роздає зміни. Без [SharedBackend] (варіант play, немає налаштувань Firebase) — вимкнено.
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

    /** Під'єднати сервер і почати стежити за всіма чатами в хмарі. */
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

    private fun ctx(): Context = checkNotNull(app)

    private suspend fun me(b: SharedBackend): Member = Member(b.signIn(), Prefs.displayName(ctx()).ifBlank { "?" })

    // ---- Хмара для всіх чатів ----

    fun cloudEnabled(): Boolean = backend != null && app?.let { Prefs.cloud(it) } == true

    fun account(): String? = backend?.account()

    /** Можна ввімкнути «Зберігати в хмарі» (Firebase і вхід через Google налаштовано). */
    fun cloudSupported(): Boolean = backend?.canUseGoogle() == true

    /** Справді спільний (є інші учасники), а не лише копія в хмарі. */
    fun isShared(chat: Chat): Boolean = chat.remoteId?.let { id -> app?.let { Prefs.isShared(it, id) } } == true

    /**
     * Увімкнути хмару: вхід через Google, повернути чати з інших телефонів цього акаунта,
     * а все, що досі лише на телефоні, — вивантажити. Повертає пошту акаунта.
     */
    suspend fun enableCloud(activity: Context): String {
        val b = checkNotNull(backend)
        val email = b.signInWithGoogle(activity)
        val me = me(b)
        // Якщо акаунт уже був на іншому телефоні, ідентифікатор змінився — повертаємося у свої чати за кодами.
        val mine = b.myChats().mapTo(HashSet()) { it.id }
        for (chat in Repo.sharedChats()) {
            val remoteId = chat.remoteId ?: continue
            if (remoteId in mine) continue
            Prefs.inviteCode(ctx(), remoteId)?.let { b.joinChat(it, me) }
        }
        // Чати з інших телефонів.
        val remote = b.myChats()
        for (rc in remote) {
            val chatId = Repo.chatByRemoteId(rc.id)?.id ?: Repo.createSharedChat(rc.name, rc.id)
            if (rc.inviteCode.isNotEmpty()) Prefs.setInviteCode(ctx(), rc.id, rc.inviteCode)
            if (rc.members > 1) Prefs.setShared(ctx(), rc.id)
            Repo.applyRemote(chatId, b.fetch(rc.id), me.uid)
        }
        Prefs.setCloud(ctx(), true)
        // Порожній чат «за замовчуванням» нового телефона не дублюємо, якщо такий уже повернувся з хмари.
        val names = remote.mapTo(HashSet()) { it.name }
        for (chat in Repo.allChats()) {
            if (chat.remoteId != null) continue
            if (chat.name in names && Repo.remindersOf(chat.id).isEmpty()) Repo.deleteChat(chat) else upload(b, chat, me)
        }
        Repo.sharedChats().forEach { watch(it) }
        return email
    }

    /** Нові чати більше не потрапляють у хмару; наявні копії лишаються (спільні працюють і далі). */
    fun disableCloud() {
        app?.let { Prefs.setCloud(it, false) }
    }

    /** Чат лише з цього телефона — у хмару (поки без учасників). */
    private suspend fun upload(b: SharedBackend, chat: Chat, me: Member): RemoteChat {
        val remote = b.createChat(chat.name, me)
        Repo.setChatRemoteId(chat.id, remote.id)
        Prefs.setInviteCode(ctx(), remote.id, remote.inviteCode)
        Repo.remindersOf(chat.id).filter { it.kind == Kind.TEXT }.forEach { push(b, remote.id, it, me) }
        watch(chat.copy(remoteId = remote.id))
        return remote
    }

    // ---- Спільні чати ----

    /** Поділитися чатом; повертає код запрошення. Наявні текстові нагадування стають спільними. */
    suspend fun share(chat: Chat): String {
        val b = checkNotNull(backend)
        val remoteId = chat.remoteId
        val code = if (remoteId == null) {
            val remote = upload(b, chat, me(b))
            Prefs.setShared(ctx(), remote.id)
            remote.inviteCode
        } else {
            Prefs.setShared(ctx(), remoteId)
            Prefs.inviteCode(ctx(), remoteId) ?: b.myChats().firstOrNull { it.id == remoteId }?.inviteCode.orEmpty()
        }
        return code
    }

    /** Приєднатися за кодом; повертає локальний чат або null, якщо код не знайдено. */
    suspend fun join(code: String): Long? {
        val b = checkNotNull(backend)
        val me = me(b)
        val remote = b.joinChat(code.trim().uppercase(), me) ?: return null
        val chatId = Repo.chatByRemoteId(remote.id)?.id ?: Repo.createSharedChat(remote.name, remote.id)
        Prefs.setInviteCode(ctx(), remote.id, remote.inviteCode)
        Prefs.setShared(ctx(), remote.id)
        Repo.applyRemote(chatId, b.fetch(remote.id), me.uid)
        Repo.chatById(chatId)?.let { watch(it) }
        return chatId
    }

    /** Разова синхронізація всіх чатів у хмарі (фонова перевірка, коли застосунок закрито). */
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

    /** Новий чат — у хмару, якщо вона ввімкнена. */
    fun onChatCreated(id: Long) {
        val b = backend ?: return
        if (!cloudEnabled()) return
        send {
            val chat = Repo.chatById(id) ?: return@send
            if (chat.remoteId == null) upload(b, chat, me(b))
        }
    }

    fun onChatRenamed(chat: Chat, name: String) {
        val b = backend ?: return
        val remoteId = chat.remoteId ?: return
        send { b.renameChat(remoteId, name.trim()) }
    }

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

    /** Видалили чат у себе — виходимо з нього (останній учасник видаляє його й із хмари). */
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
