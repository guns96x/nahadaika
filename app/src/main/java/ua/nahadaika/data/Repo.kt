package ua.nahadaika.data

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.nahadaika.R
import ua.nahadaika.Res
import ua.nahadaika.alarm.AlarmScheduler
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.share.ChatSnapshot
import ua.nahadaika.share.SharedChats
import ua.nahadaika.widget.ReminderWidget
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/** Єдина точка роботи з даними: БД + будильники + сповіщення. */
@SuppressLint("StaticFieldLeak") // тримаємо лише applicationContext
object Repo {
    private lateinit var app: Context
    private lateinit var db: AppDatabase

    /** Серіалізує доставку, щоб будильник і перепланування не надіслали сповіщення двічі. */
    private val lock = Mutex()

    val chatColors = listOf(
        0xFF2AABEE, 0xFFE17076, 0xFF7BC862, 0xFFEE7AAE,
        0xFF6EC9CB, 0xFFFAA774, 0xFF65AADD, 0xFFA695E7,
    ).map { it.toInt() }

    private var widgetJob: Job? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (::app.isInitialized && app === appContext) return
        app = appContext
        db = AppDatabase.create(app)
        widgetJob?.cancel()
        widgetJob = null
        // Без віджета на екрані не чіпаємо базу при старті.
        if (ReminderWidget.isPlaced(app)) watchWidget()
    }

    /** Будь-яка зміна нагадувань чи чатів — перемалювати віджет. Вмикається, щойно віджет з'являється на екрані. */
    @OptIn(FlowPreview::class)
    fun watchWidget() {
        if (widgetJob?.isActive == true) return
        widgetJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            merge(db.reminders().observeAll().map { }, db.chats().observeAll().map { })
                .debounce(300)
                .collect { ReminderWidget.refresh(app) }
        }
    }

    /** Найближче нагадування, що ще не спрацювало, і назва його чату — для віджета. */
    suspend fun nextPending(): Pair<Reminder, String?>? {
        val r = db.reminders().pending().minByOrNull { it.alarmAt() } ?: return null
        return r to db.chats().get(r.chatId)?.name
    }

    // ---- Чати ----

    val chats: Flow<List<Chat>> get() = db.chats().observeAll()
    val allReminders: Flow<List<Reminder>> get() = db.reminders().observeAll()

    fun chat(id: Long): Flow<Chat?> = db.chats().observe(id)
    fun reminders(chatId: Long): Flow<List<Reminder>> = db.reminders().observeByChat(chatId)

    suspend fun ensureDefaultChat() {
        if (db.chats().count() == 0) createChat(Res.s(R.string.core_default_chat_name))
    }

    suspend fun createChat(name: String): Long {
        val color = chatColors[(db.chats().count()) % chatColors.size]
        return db.chats().insert(Chat(name = name.trim(), color = color))
    }

    suspend fun renameChat(chat: Chat, name: String) = db.chats().update(chat.copy(name = name.trim()))

    suspend fun deleteChat(chat: Chat) {
        db.reminders().byChat(chat.id).forEach { cleanup(it) }
        db.chats().delete(chat)
        SharedChats.onChatDeleted(chat)
    }

    // ---- Нагадування ----

    suspend fun createReminder(reminder: Reminder): Long {
        val id = db.reminders().insert(reminder)
        AlarmScheduler.schedule(app, reminder.copy(id = id))
        SharedChats.onReminderChanged(id)
        return id
    }

    suspend fun reschedule(reminder: Reminder, at: Long, repeat: Repeat) = lock.withLock {
        val updated = reminder.copy(triggerAt = at, repeat = repeat, snoozedUntil = null, fired = false)
        db.reminders().update(updated)
        AlarmScheduler.schedule(app, updated)
        SharedChats.onReminderChanged(updated.id)
    }

    suspend fun editText(reminder: Reminder, text: String) {
        db.reminders().get(reminder.id)?.let { db.reminders().update(it.copy(text = text.trim())) }
        SharedChats.onReminderChanged(reminder.id)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        val current = db.reminders().get(reminder.id) ?: reminder
        cleanup(current)
        db.reminders().delete(current)
        SharedChats.onReminderDeleted(current)
    }

    // ---- Обговорення ----

    fun comments(reminderId: Long): Flow<List<Comment>> = db.comments().observeByReminder(reminderId)
    fun commentStats(chatId: Long): Flow<List<CommentStat>> = db.comments().observeStats(chatId)

    suspend fun addComment(reminderId: Long, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val comment = Comment(reminderId = reminderId, text = t)
        SharedChats.onCommentAdded(comment.copy(id = db.comments().insert(comment)))
    }

    /** Повідомлення від іншого учасника спільного чату (з синхронізації). */
    suspend fun receiveComment(reminderId: Long, text: String, authorName: String, createdAt: Long = System.currentTimeMillis(), remoteId: String? = null) {
        db.comments().insert(Comment(reminderId = reminderId, text = text, authorName = authorName, createdAt = createdAt, remoteId = remoteId))
    }

    /** Обговорення переглянуто — позначка «нове» зникає. */
    suspend fun markCommentsRead(reminderId: Long) {
        db.reminders().get(reminderId)?.let { db.reminders().update(it.copy(commentsReadAt = System.currentTimeMillis())) }
    }

    /**
     * «Готово» без очікування: разове йде в історію, повторюване — на наступний раз.
     * Сповіщення, якщо вже висить, прибираємо.
     */
    suspend fun markDone(id: Long) = lock.withLock {
        val r = db.reminders().get(id) ?: return@withLock
        val now = System.currentTimeMillis()
        val updated = if (r.repeat == Repeat.NONE) {
            r.copy(fired = true, snoozedUntil = null, lastFiredAt = now)
        } else {
            r.copy(triggerAt = nextOccurrence(r.triggerAt, r.repeat, maxOf(now, r.triggerAt)), snoozedUntil = null, lastFiredAt = now)
        }
        db.reminders().update(updated)
        Notifier.cancel(app, id)
        if (updated.fired) AlarmScheduler.cancel(app, id) else AlarmScheduler.schedule(app, updated)
        SharedChats.onReminderChanged(id)
    }

    /** Прибрати виконані нагадування разом з медіафайлами; [chatId] = null — у всіх чатах. */
    suspend fun clearDone(chatId: Long? = null): Int {
        val done = db.reminders().done().filter { chatId == null || it.chatId == chatId }
        done.forEach { deleteReminder(it) }
        return done.size
    }

    // ---- Спільні чати ----

    suspend fun sharedChats(): List<Chat> = db.chats().shared()
    suspend fun chatById(id: Long): Chat? = db.chats().get(id)
    suspend fun chatByRemoteId(remoteId: String): Chat? = db.chats().byRemoteId(remoteId)
    suspend fun reminder(id: Long): Reminder? = db.reminders().get(id)
    suspend fun remindersOf(chatId: Long): List<Reminder> = db.reminders().byChat(chatId)

    suspend fun setChatRemoteId(chatId: Long, remoteId: String) {
        db.chats().get(chatId)?.let { db.chats().update(it.copy(remoteId = remoteId)) }
    }

    suspend fun setReminderRemoteId(id: Long, remoteId: String) = db.reminders().setRemoteId(id, remoteId)
    suspend fun setCommentRemoteId(id: Long, remoteId: String) = db.comments().setRemoteId(id, remoteId)

    suspend fun createSharedChat(name: String, remoteId: String): Long {
        val color = chatColors[(db.chats().count()) % chatColors.size]
        return db.chats().insert(Chat(name = name.trim(), color = color, remoteId = remoteId))
    }

    /**
     * Злити стан спільного чату з сервера: нове — додати, змінене — оновити, видалене — прибрати.
     * Будильники ставить кожен телефон сам. Разове з минулого (історія чату) не дзвонить запізно,
     * повторюване переходить на найближчий раз. Свої зміни сюди повертаються «луною» й нічого не міняють.
     */
    suspend fun applyRemote(chatId: Long, snapshot: ChatSnapshot, myUid: String) = lock.withLock {
        val now = System.currentTimeMillis()
        for (rr in snapshot.reminders) {
            val author = if (rr.authorUid == myUid) null else rr.authorName
            val target = if (rr.repeat != Repeat.NONE && rr.triggerAt <= now) nextOccurrence(rr.triggerAt, rr.repeat, now) else rr.triggerAt
            val local = db.reminders().byRemoteId(rr.id)
            if (local == null) {
                val fired = rr.done || (rr.repeat == Repeat.NONE && rr.triggerAt <= now)
                val created = Reminder(
                    chatId = chatId, kind = Kind.TEXT, text = rr.text, triggerAt = target, repeat = rr.repeat,
                    alarm = rr.alarm, fired = fired, createdAt = rr.createdAt, authorName = author, remoteId = rr.id,
                )
                val id = db.reminders().insert(created)
                if (!fired) AlarmScheduler.schedule(app, created.copy(id = id))
                continue
            }
            val moved = target != local.triggerAt
            val fired = when {
                rr.repeat != Repeat.NONE -> false
                rr.done -> true
                moved -> rr.triggerAt <= now
                else -> local.fired
            }
            val updated = local.copy(
                text = rr.text,
                triggerAt = if (moved) target else local.triggerAt,
                repeat = rr.repeat,
                alarm = rr.alarm,
                fired = fired,
                snoozedUntil = if (moved || fired) null else local.snoozedUntil,
                authorName = author,
            )
            if (updated == local) continue
            db.reminders().update(updated)
            if (updated.fired) {
                AlarmScheduler.cancel(app, local.id)
                Notifier.cancel(app, local.id)
            } else {
                AlarmScheduler.schedule(app, updated)
            }
        }
        val remoteIds = snapshot.reminders.mapTo(HashSet()) { it.id }
        db.reminders().byChat(chatId).filter { it.remoteId != null && it.remoteId !in remoteIds }.forEach {
            cleanup(it)
            db.reminders().delete(it)
        }
        for (rc in snapshot.comments) {
            if (db.comments().byRemoteId(rc.id) != null) continue
            val reminder = db.reminders().byRemoteId(rc.reminderId) ?: continue
            db.comments().insert(
                Comment(
                    reminderId = reminder.id, text = rc.text, createdAt = rc.createdAt, remoteId = rc.id,
                    authorName = if (rc.authorUid == myUid) null else rc.authorName,
                ),
            )
        }
    }

    // ---- Резервна копія ----

    suspend fun exportBackup(out: OutputStream) = Backup.export(db, out)

    /** Додає дані з копії до наявних і переставляє будильники. */
    suspend fun importBackup(input: InputStream): ImportResult {
        val result = Backup.import(app, db, input)
        rescheduleAll()
        return result
    }

    private fun cleanup(reminder: Reminder) {
        AlarmScheduler.cancel(app, reminder.id)
        Notifier.cancel(app, reminder.id)
        reminder.mediaPath?.let { File(it).delete() }
    }

    /** Відкласти вже показане нагадування. */
    suspend fun snooze(id: Long, minutes: Long) = lock.withLock {
        val r = db.reminders().get(id) ?: return@withLock
        val updated = r.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000, fired = false)
        db.reminders().update(updated)
        AlarmScheduler.schedule(app, updated)
        Notifier.cancel(app, id)
    }

    /**
     * Показати сповіщення й перевести нагадування у наступний стан.
     * [force] — "Надіслати зараз" з інтерфейсу, незалежно від часу.
     */
    suspend fun deliver(id: Long, force: Boolean = false) = lock.withLock {
        val r = db.reminders().get(id) ?: return@withLock
        val now = System.currentTimeMillis()
        if (!force && (r.fired || r.alarmAt() > now + 5_000)) {
            // Уже оброблено або будильник спрацював зарано — просто переставляємо.
            if (!r.fired) AlarmScheduler.schedule(app, r)
            return@withLock
        }

        val chatName = db.chats().get(r.chatId)?.name ?: Res.s(R.string.core_chat_fallback_name)
        Notifier.show(app, r, chatName)

        val updated = when {
            force && r.repeat != Repeat.NONE -> r
            force || r.repeat == Repeat.NONE -> r.copy(fired = true, snoozedUntil = null)
            r.snoozedUntil != null -> r.copy(snoozedUntil = null)
            else -> r.copy(triggerAt = nextOccurrence(r.triggerAt, r.repeat, now))
        }.copy(lastFiredAt = now)

        db.reminders().update(updated)
        if (updated.fired) AlarmScheduler.cancel(app, id) else AlarmScheduler.schedule(app, updated)
    }

    /** Після перезавантаження, зміни часу чи запуску застосунку: переставити всі будильники. */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        db.reminders().pending().forEach { r ->
            if (r.alarmAt() <= now) deliver(r.id) else AlarmScheduler.schedule(app, r)
        }
    }

    /** Наступне повторення, строго пізніше за [now]. Рахується від початкової дати, щоб 31-ше число не "з'їжджало". */
    fun nextOccurrence(from: Long, repeat: Repeat, now: Long): Long {
        if (repeat == Repeat.NONE) return from
        val base = Instant.ofEpochMilli(from).atZone(ZoneId.systemDefault())
        var k = 1L
        while (true) {
            val next = when (repeat) {
                Repeat.DAILY -> base.plusDays(k)
                Repeat.WEEKLY -> base.plusWeeks(k)
                Repeat.MONTHLY -> base.plusMonths(k)
                Repeat.YEARLY -> base.plusYears(k)
                Repeat.NONE -> base
            }.toInstant().toEpochMilli()
            if (next > now) return next
            k++
        }
    }
}
