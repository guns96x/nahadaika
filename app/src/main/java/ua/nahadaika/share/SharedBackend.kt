package ua.nahadaika.share

import kotlinx.coroutines.flow.Flow
import ua.nahadaika.data.Repeat

/** Учасник спільного чату, як його бачать інші. */
data class Member(val uid: String, val name: String)

/** Нагадування на сервері. Поки спільні лише текстові нагадування (голосові й відео — наступним кроком). */
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
)

/** Повідомлення в обговоренні нагадування [reminderId]. */
data class RemoteComment(
    val id: String,
    val reminderId: String,
    val text: String,
    val authorUid: String,
    val authorName: String,
    val createdAt: Long,
)

data class RemoteChat(val id: String, val name: String, val inviteCode: String)

/** Усе, що зараз лежить у спільному чаті на сервері. */
data class ChatSnapshot(val reminders: List<RemoteReminder>, val comments: List<RemoteComment>)

/**
 * Сервер спільних чатів. Реалізація — у варіанті github (Firebase); у тестах — підміна в пам'яті.
 * Будильники ставить кожен телефон сам, сервер лише зберігає й роздає зміни.
 */
interface SharedBackend {
    /** Невидимий вхід (без акаунта); повертає ідентифікатор цього телефона. */
    suspend fun signIn(): String

    fun newId(): String

    suspend fun createChat(name: String, me: Member): RemoteChat

    /** null — такого коду запрошення немає. */
    suspend fun joinChat(code: String, me: Member): RemoteChat?

    suspend fun fetch(chatId: String): ChatSnapshot

    fun observe(chatId: String): Flow<ChatSnapshot>

    /** [create] — нове нагадування (з автором); інакше оновлюються лише текст, час, повтор, будильник і «готово». */
    suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean)

    suspend fun deleteReminder(chatId: String, reminderId: String)

    suspend fun putComment(chatId: String, comment: RemoteComment)

    suspend fun leave(chatId: String, me: Member)
}
