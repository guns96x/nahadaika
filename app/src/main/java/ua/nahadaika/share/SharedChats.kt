package ua.nahadaika.share

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.File

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
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    suspend fun enableCloud(activity: Context): String = outgoing.withLock {
        val b = checkNotNull(backend)
        val email = b.signInWithGoogle(activity)
        val me = me(b)

        // Скидаємо попередні слухачі, щоб вони не працювали зі застарілим UID
        watching.values.forEach { it.cancel() }
        watching.clear()

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
            val snapshot = b.fetch(rc.id)
            Repo.applyRemote(chatId, snapshot, me.uid)
            syncMedia(b, chatId, snapshot)
        }
        Prefs.setCloud(ctx(), true)
        // Порожній чат «за замовчуванням» нового телефона не дублюємо, якщо такий уже повернувся з хмари.
        val names = remote.mapTo(HashSet()) { it.name }
        for (chat in Repo.allChats()) {
            if (chat.remoteId != null) continue
            if (chat.name in names && Repo.remindersOf(chat.id).isEmpty()) Repo.deleteChat(chat) else upload(b, chat, me)
        }
        Repo.sharedChats().forEach { watch(it) }
        email
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
        Repo.remindersOf(chat.id).forEach { push(b, remote.id, it, me) }
        watch(chat.copy(remoteId = remote.id))
        return remote
    }

    // ---- Спільні чати ----

    /** Поділитися чатом; повертає код запрошення. Наявні нагадування стають спільними. */
    suspend fun share(chat: Chat): String = outgoing.withLock {
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
        code
    }

    /** Приєднатися за кодом; повертає локальний чат або null, якщо код не знайдено. */
    suspend fun join(code: String): Long? = outgoing.withLock {
        val b = checkNotNull(backend)
        val me = me(b)
        val remote = b.joinChat(code.trim().uppercase(), me) ?: return@withLock null
        val chatId = Repo.chatByRemoteId(remote.id)?.id ?: Repo.createSharedChat(remote.name, remote.id)
        Prefs.setInviteCode(ctx(), remote.id, remote.inviteCode)
        Prefs.setShared(ctx(), remote.id)
        val snapshot = b.fetch(remote.id)
        Repo.applyRemote(chatId, snapshot, me.uid)
        syncMedia(b, chatId, snapshot)
        Repo.chatById(chatId)?.let { watch(it) }
        chatId
    }

    /** Разова синхронізація всіх чатів у хмарі (фонова перевірка, коли застосунок закрито). */
    suspend fun syncOnce() = outgoing.withLock {
        val b = backend ?: return@withLock
        val context = ctx()
        val me = me(b)

        // 1. Повторити видалення, які сталися офлайн
        for ((chatRemoteId, reminderRemoteId) in SyncQueue.allPendingDeletions(context)) {
            runCatching {
                b.deleteReminder(chatRemoteId, reminderRemoteId)
                SyncQueue.removePendingDeletion(context, chatRemoteId, reminderRemoteId)
            }
        }

        // 2. Повторити вивантаження локальних змін
        for (chat in Repo.sharedChats()) {
            val remoteChatId = chat.remoteId ?: continue
            val pendingReminders = Repo.remindersOf(chat.id).filter { it.remoteId == null }
            for (r in pendingReminders) {
                runCatching { push(b, remoteChatId, r, me) }
            }
            for (r in Repo.remindersOf(chat.id)) {
                if (r.remoteId == null) continue
                for (comment in Repo.commentsOf(r.id).filter { it.remoteId == null && it.authorName == null }) {
                    runCatching { pushComment(b, remoteChatId, r.remoteId, comment, me) }
                }
            }
            val pendingUpdates = SyncQueue.allPendingUpdates(context)
            for (updateId in pendingUpdates) {
                val r = Repo.reminder(updateId)
                if (r != null && r.chatId == chat.id && r.remoteId != null) {
                    runCatching { push(b, remoteChatId, r, me) }
                }
            }
        }

        // 3. Завантажити оновлення з сервера та синхронізувати медіа
        for (chat in Repo.sharedChats()) {
            val remoteChatId = chat.remoteId ?: continue
            val snapshot = b.fetch(remoteChatId)
            Repo.applyRemote(chat.id, snapshot, me.uid)
            syncMedia(b, chat.id, snapshot)
            watch(chat)
        }
    }

    private fun watch(chat: Chat) {
        val b = backend ?: return
        val remoteId = chat.remoteId ?: return
        if (watching[remoteId]?.isActive == true) return
        watching[remoteId] = scope.launch {
            runCatching {
                b.observe(remoteId).collect { snapshot ->
                    outgoing.withLock {
                        val currentUid = b.signIn()
                        val localChat = Repo.chatByRemoteId(remoteId) ?: return@withLock
                        Repo.applyRemote(localChat.id, snapshot, currentUid)
                        syncMedia(b, localChat.id, snapshot)
                    }
                }
            }
        }
    }

    private suspend fun syncMedia(b: SharedBackend, chatId: Long, snapshot: ChatSnapshot) {
        if (!b.storageAvailable()) return
        val remoteChat = Repo.chatById(chatId)?.remoteId ?: return
        for (rr in snapshot.reminders) {
            if (rr.kind == Kind.TEXT || rr.mediaRef == null || !MediaSync.isSafeMediaRef(rr.mediaRef)) continue
            if (!rr.mediaRef.startsWith("chats/$remoteChat/media/${rr.id}.")) continue
            if (rr.mediaSize !in 1..MediaSync.MAX_MEDIA_SIZE_BYTES) continue
            val local = Repo.reminderByRemoteId(rr.id) ?: continue
            if (local.mediaPath != null && File(local.mediaPath).exists()) continue

            val downloadedPath = downloadMedia(b, rr.mediaRef, rr.id)
            if (downloadedPath != null) {
                Repo.updateReminderMediaPath(local.id, downloadedPath)
            }
        }
    }

    private suspend fun downloadMedia(b: SharedBackend, mediaRef: String, reminderId: String): String? {
        val context = ctx()
        if (!MediaSync.isSafeMediaRef(mediaRef)) return null
        val ext = mediaRef.substringAfterLast('.', "bin")
        val targetFile = runCatching { MediaSync.localTargetFile(context, reminderId, ext) }.getOrNull() ?: return null
        if (targetFile.exists() && targetFile.length() > 0) return targetFile.absolutePath

        val tempFile = runCatching { MediaSync.localTempFile(context, reminderId, ext) }.getOrNull() ?: return null
        val success = runCatching { b.downloadMedia(mediaRef, tempFile) }.getOrDefault(false)
        return if (success && MediaSync.atomicPromote(tempFile, targetFile)) {
            targetFile.absolutePath
        } else {
            tempFile.delete()
            null
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
        SyncQueue.recordPendingUpdate(ctx(), id)
        send {
            val r = Repo.reminder(id) ?: return@send
            // Чат лише на телефоні — у черзі правок йому нічого чекати.
            val remoteChat = Repo.chatById(r.chatId)?.remoteId ?: run {
                SyncQueue.removePendingUpdate(ctx(), id)
                return@send
            }
            push(b, remoteChat, r, me(b))
        }
    }

    fun onReminderDeleted(reminder: Reminder, remoteChat: String?) {
        val b = backend ?: return
        val context = ctx()
        val remoteId = reminder.remoteId ?: SyncQueue.pendingRemoteId(context, reminder.id)
        if (remoteChat == null || remoteId == null) {
            SyncQueue.removePendingUpdate(context, reminder.id)
            SyncQueue.clearReservation(context, "reminder", reminder.id)
            return
        }
        // Записуємо надгробок до локального видалення та будь-якого очікування мережі.
        SyncQueue.recordPendingDeletion(context, remoteChat, remoteId)
        SyncQueue.removePendingUpdate(context, reminder.id)
        SyncQueue.clearReservation(context, "reminder", reminder.id)
        send {
            b.deleteReminder(remoteChat, remoteId)
            SyncQueue.removePendingDeletion(context, remoteChat, remoteId)
        }
    }

    fun onCommentAdded(comment: Comment) {
        val b = backend ?: return
        send {
            val r = Repo.reminder(comment.reminderId) ?: return@send
            val remoteChat = Repo.chatById(r.chatId)?.remoteId ?: return@send
            val remoteReminder = r.remoteId ?: return@send
            val me = me(b)
            pushComment(b, remoteChat, remoteReminder, comment, me)
        }
    }

    private suspend fun pushComment(b: SharedBackend, remoteChat: String, remoteReminder: String, comment: Comment, me: Member) {
        val id = SyncQueue.reserveId(ctx(), "comment", comment.id, b::newId)
        b.putComment(remoteChat, RemoteComment(id, remoteReminder, comment.text, me.uid, me.name, comment.createdAt))
        Repo.setCommentRemoteId(comment.id, id)
        SyncQueue.clearReservation(ctx(), "comment", comment.id)
    }

    /** Видалили чат у себе — виходимо з нього (останній учасник видаляє його й із хмари). */
    fun onChatDeleted(chat: Chat) {
        val b = backend ?: return
        val remoteId = chat.remoteId ?: return
        watching.remove(remoteId)?.cancel()
        send { b.leave(remoteId, me(b)) }
    }

    private suspend fun push(b: SharedBackend, remoteChat: String, r: Reminder, me: Member) {
        val revision = SyncQueue.revision(ctx(), r.id)
        val create = r.remoteId == null
        // Файл вивантажуємо лише разом зі створенням: правки часу чи тексту не женуть відео вдруге,
        // а чуже медіа, яке ще не докачалося, все одно можна перенести.
        val file = if (create && r.kind != Kind.TEXT) {
            val f = r.mediaPath?.let(::File)
            val ok = b.storageAvailable() && f != null && MediaSync.isLocalMedia(ctx(), f) &&
                f.length() in 1..MediaSync.MAX_MEDIA_SIZE_BYTES
            if (!ok) {
                // Без Storage (чи без файлу) медіа лишається лише на цьому телефоні.
                SyncQueue.removePendingUpdate(ctx(), r.id)
                return
            }
            f
        } else null
        val id = r.remoteId ?: SyncQueue.reserveId(ctx(), "reminder", r.id, b::newId)
        val mime = file?.let { MediaSync.mimeFor(r.kind, it.extension) }
        val mediaRef = file?.let { b.uploadMedia(remoteChat, id, it, mime!!) ?: return }

        try {
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
                    kind = r.kind,
                    mediaRef = mediaRef,
                    durationMs = r.durationMs,
                    mediaSize = file?.length() ?: 0,
                    mediaMime = mime,
                ),
                create,
            )
        } catch (_: RemoteGone) {
            // Поки правили офлайн, інший учасник видалив запис — видалення перемагає, інакше лишиться «зомбі».
            SyncQueue.removePendingUpdate(ctx(), r.id)
            Repo.forgetRemote(r.id)
            return
        }
        // Лише після успішного збереження на сервері записуємо remoteId локально
        Repo.setReminderRemoteId(r.id, id)
        SyncQueue.removePendingUpdate(ctx(), r.id, revision)
        SyncQueue.clearReservation(ctx(), "reminder", r.id)
    }
}
