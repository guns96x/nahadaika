package ua.nahadaika

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.nahadaika.data.Chat
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder

class SearchTest {
    private val chats = listOf(Chat(id = 1, name = "Дім", color = 0), Chat(id = 2, name = "Робота", color = 0))

    private fun r(id: Long, chat: Long, text: String, at: Long, kind: Kind = Kind.TEXT, fired: Boolean = false, lastFired: Long? = null) =
        Reminder(id = id, chatId = chat, kind = kind, text = text, triggerAt = at, fired = fired, lastFiredAt = lastFired)

    private val all = listOf(
        r(1, 1, "Купити хліб", at = 300),
        r(2, 1, "Полити квіти", at = 100),
        r(3, 2, "Нарада з Андрієм", at = 200),
        r(4, 2, "Здати звіт", at = 50, fired = true, lastFired = 60),
        r(5, 1, "", at = 400, kind = Kind.VOICE),
        r(6, 1, "Старе", at = 10, fired = true, lastFired = 900),
    )

    private fun ids(query: String, filter: SearchFilter = SearchFilter.ALL) =
        searchReminders(all, chats, query, filter).map { it.reminder.id }

    @Test fun emptyQueryListsPendingByTimeThenDoneNewestFirst() = assertEquals(listOf(2L, 3L, 1L, 5L, 6L, 4L), ids(""))

    @Test fun caseInsensitiveTextMatch() = assertEquals(listOf(1L), ids("ХЛІБ"))

    @Test fun everyWordMustMatch() {
        assertEquals(listOf(3L), ids("нарада андрієм"))
        assertEquals(emptyList<Long>(), ids("нарада хліб"))
    }

    @Test fun matchesChatName() = assertEquals(listOf(3L, 4L), ids("робота").sorted())

    @Test fun matchesMediaWithoutCaption() = assertEquals(listOf(5L), ids("голосове"))

    @Test fun filtersPendingAndDone() {
        assertEquals(listOf(2L, 3L, 1L, 5L), ids("", SearchFilter.PENDING))
        assertEquals(listOf(6L, 4L), ids("", SearchFilter.DONE))
    }

    @Test fun hitCarriesChat() = assertEquals("Робота", searchReminders(all, chats, "звіт", SearchFilter.ALL).single().chat?.name)
}
