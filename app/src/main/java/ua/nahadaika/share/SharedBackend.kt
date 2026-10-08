package ua.nahadaika.share

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Repeat
import java.io.File

/** Учасник спільного чату, як його бачать інші. */
data class Member(val uid: String, val name: String)

/** Нагадування на сервері: текст або медіа (голосові, відео, фото). */
data class RemoteReminder(
    val id: String,
    val text: String,
    val triggerAt: Long,
    val repeat: Repeat,
    val alarm: Boolean,
    val done: Boolean,
    val authorUid: String,
    val authorName: String,
    val createdAt: Long,
    val kind: Kind = Kind.TEXT,
    /** Приватний детермінований шлях у Storage (наприклад, chats/{chatId}/media/{id}.ext). */
    val mediaRef: String? = null,
    val durationMs: Long = 0,
    val mediaSize: Long = 0,
    val mediaMime: String? = null,
)

/** Оновлювали нагадування, якого на сервері вже немає (видалив інший учасник). */
class RemoteGone : Exception()

/** Повідомлення в обговоренні нагадування [reminderId]. */
data class RemoteComment(
    val id: String,
    val reminderId: String,
    val text: String,
    val authorUid: String,
    val authorName: String,
    val createdAt: Long,
)

/** Чат на сервері; [members] > 1 — справді спільний, інакше лише копія в хмарі. */
data class RemoteChat(val id: String, val name: String, val inviteCode: String, val members: Int = 1)

/** Усе, що зараз лежить у чаті на сервері; [name] = null — назву не відомо (не міняти). */
data class ChatSnapshot(
    val reminders: List<RemoteReminder>,
    val comments: List<RemoteComment>,
    val name: String? = null,
)

/**
 * Сервер чатів у хмарі. Реалізація — у варіанті github (Firebase); у тестах — підміна в пам'яті.
 * Будильники ставить кожен телефон сам, сервер лише зберігає й роздає зміни.
 */
interface SharedBackend {
    /** Вхід без акаунта (анонімно) або вже наявний; повертає ідентифікатор користувача. */
    suspend fun signIn(): String

    /**
     * Вхід через Google — та сама хмара на всіх телефонах. Анонімний вхід прив'язується до акаунта,
     * тож спільні чати не губляться. Повертає пошту акаунта.
     */
    suspend fun signInWithGoogle(activity: Context): String

    /** Пошта, якщо ввійшли через Google; null — анонімно або ще ніяк. */
    fun account(): String?

    /** Вхід через Google налаштовано (є OAuth-клієнт) — можна вмикати хмару. */
    fun canUseGoogle(): Boolean

    fun newId(): String

    suspend fun createChat(name: String, me: Member): RemoteChat

    /** null — такого коду запрошення немає. */
    suspend fun joinChat(code: String, me: Member): RemoteChat?

    /** Усі чати, де я учасник (відновлення на новому телефоні). */
    suspend fun myChats(): List<RemoteChat>

    suspend fun renameChat(chatId: String, name: String)

    suspend fun fetch(chatId: String): ChatSnapshot

    fun observe(chatId: String): Flow<ChatSnapshot>

    /** [create] — нове нагадування (з автором); інакше оновлюються лише текст, час, повтор, будильник і «готово». */
    suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean)

    suspend fun deleteReminder(chatId: String, reminderId: String)

    suspend fun putComment(chatId: String, comment: RemoteComment)

    /** Вийти з чату; якщо я був останнім — чат видаляється. */
    suspend fun leave(chatId: String, me: Member)

    /** Медіа можна передати іншим: є Firebase Storage або «кур'єр» у Firestore. */
    fun storageAvailable(): Boolean = false

    /**
     * Медіа лежить у хмарі лише до доставки всім учасникам (кур'єр), а не зберігається там.
     * Тоді в чат без інших учасників (особиста копія в хмарі) його не вивантажують.
     */
    fun mediaIsTemporary(): Boolean = false

    /**
     * Вивантажити медіафайл (перед відправкою його можна стиснути). Повертає, що саме лежить на сервері,
     * або null — цей файл не передати (немає сховища, завеликий): він лишається лише на телефоні.
     * Мережеві збої — винятком, тоді запис повториться пізніше.
     */
    suspend fun uploadMedia(chatId: String, reminderId: String, file: File, mime: String): UploadedMedia? = null

    /** Завантажити медіафайл за [mediaRef] у локальний файл [target]; true — успішно. */
    suspend fun downloadMedia(mediaRef: String, target: File): Boolean = false

    /** Файл [mediaRef] уже на цьому телефоні: кур'єр прибирає його з хмари, щойно отримали всі. */
    suspend fun mediaReceived(chatId: String, mediaRef: String) = Unit

    /** Прибрати з хмари медіа чату, яке ніхто не забрав за 30 днів. */
    suspend fun cleanupMedia(chatId: String) = Unit

    /** Зареєструвати цей телефон для миттєвих сповіщень про зміни в чаті. */
    suspend fun registerPush(chatId: String) = Unit

    /**
     * Імена учасників, у яких стара версія застосунку: вони не отримують миттєвих сповіщень і медіа.
     * Версію кожен телефон повідомляє, реєструючись для сповіщень; хто не реєструвався — вважається старим.
     */
    suspend fun staleMembers(chatId: String): List<String> = emptyList()

    /** Куди відправити людину оновитися (сторінка останнього випуску); null — немає куди. */
    fun updateLink(): String? = null

    /** Розбудити телефони інших учасників; [urgent] — нове нагадування чи повідомлення. Збої не страшні: є фонова перевірка. */
    suspend fun notifyMembers(chatId: String, urgent: Boolean) = Unit
}

/** Медіа на сервері: шлях, розмір і тип того, що справді вивантажено (після стиснення). */
data class UploadedMedia(val ref: String, val size: Long, val mime: String)
