package ua.nahadaika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder

class TitleTest {
    private fun r(text: String, kind: Kind = Kind.TEXT, durationMs: Long = 0) =
        Reminder(chatId = 1, kind = kind, text = text, durationMs = durationMs, triggerAt = 0)

    @Test fun shortTextIsTheTitle() = assertEquals("Купити хліб", reminderTitle(r("Купити хліб")))

    @Test fun firstSentenceBecomesTitle() =
        assertEquals("Подзвонити в банк.", reminderTitle(r("Подзвонити в банк. Уточнити про кредит і ставку")))

    @Test fun tinySentenceIsNotATitle() = assertEquals("Ок. Купити хліб", reminderTitle(r("Ок. Купити хліб")))

    @Test fun firstLineOnly() = assertEquals("Список у магазин", reminderTitle(r("Список у магазин\nхліб, молоко, сир")))

    @Test fun longTextCutAtWord() {
        val title = reminderTitle(r("Забрати посилку на пошті і не забути паспорт бо без нього не віддадуть"))
        assertEquals("Забрати посилку на пошті і не забути…", title)
        assertTrue(title.length <= 41)
    }

    @Test fun mediaWithoutCaption() {
        assertEquals("🎤 Голосове · 0:09", reminderTitle(r("", Kind.VOICE, 9_000)))
        assertEquals("🎬 Кружечок · 0:07", reminderTitle(r("", Kind.VIDEO, 7_000)))
        assertEquals("🖼 Фото", reminderTitle(r("", Kind.PHOTO)))
    }

    @Test fun mediaWithCaption() = assertEquals("🎬 Торт на день народження", reminderTitle(r("Торт на день народження", Kind.VIDEO)))

    @Test fun moreThanTitleOnlyWhenTextIsLonger() {
        assertFalse(hasMoreThanTitle(r("Купити хліб")))
        assertFalse(hasMoreThanTitle(r("Торт", Kind.VIDEO)))
        assertTrue(hasMoreThanTitle(r("Подзвонити в банк. Уточнити про кредит")))
    }
}
