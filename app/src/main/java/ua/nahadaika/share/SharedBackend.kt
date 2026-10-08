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

    /** Чи налаштовано й доступне хмарне сховище Firebase Storage для медіафайлів. */
    fun storageAvailable(): Boolean = false

    /**
     * Завантажити медіафайл до приватного автентифікованого сховища Storage.
     * Повертає відносний шлях у сховищі (mediaRef) або null, якщо сховище недоступне.
     */
    suspend fun uploadMedia(chatId: String, reminderId: String, file: File, mime: String): String? = null

    /**
     * Завантажити медіафайл зі сховища за приватним [mediaRef] у локальний файл [target].
     * Повертає true у разі успіху.
     */
    suspend fun downloadMedia(mediaRef: String, target: File): Boolean = false
}
