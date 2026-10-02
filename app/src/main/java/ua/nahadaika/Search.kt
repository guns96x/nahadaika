package ua.nahadaika

import ua.nahadaika.data.Chat
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.alarmAt
import java.util.Locale

enum class SearchFilter(val label: String) {
    ALL("Усі"),
    PENDING("Заплановані"),
    DONE("Виконані"),
}

data class SearchHit(val reminder: Reminder, val chat: Chat?)

private val uk: Locale = Locale.forLanguageTag("uk")

/** Слова, за якими знаходяться медіа без підпису: «голосове», «відео», «фото». */
private fun kindWords(kind: Kind) = when (kind) {
    Kind.TEXT -> ""
    Kind.VOICE -> "голосове голосова голосовое"
    Kind.VIDEO -> "відео відеокружечок видео"
    Kind.PHOTO -> "фото фотографія"
}

private fun normalize(s: String) = s.lowercase(uk).replace('ё', 'е').replace('’', '\'').replace('ʼ', '\'')

/**
 * Пошук по всіх чатах: кожне слово запиту має зустрічатися в тексті, назві чату чи виді медіа.
 * Порожній запит повертає все за фільтром — так «Виконані» працюють як архів.
 * Заплановані — за часом спрацювання, виконані — від найновіших.
 */
fun searchReminders(reminders: List<Reminder>, chats: List<Chat>, query: String, filter: SearchFilter): List<SearchHit> {
    val chatById = chats.associateBy { it.id }
    val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matching = reminders.filter { r ->
        when (filter) {
            SearchFilter.ALL -> true
            SearchFilter.PENDING -> !r.fired
            SearchFilter.DONE -> r.fired
        } && run {
            val haystack = normalize("${r.text} ${chatById[r.chatId]?.name.orEmpty()} ${kindWords(r.kind)}")
            terms.all { it in haystack }
        }
    }
    val (done, pending) = matching.partition { it.fired }
    return (pending.sortedBy { it.alarmAt() } + done.sortedByDescending { it.lastFiredAt ?: it.triggerAt })
        .map { SearchHit(it, chatById[it.chatId]) }
}
