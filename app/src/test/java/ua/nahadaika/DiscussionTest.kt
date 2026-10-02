package ua.nahadaika

import android.app.AlarmManager
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xxhdpi")
class DiscussionTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        Prefs.setGestureHintShown(app)
        Repo.init(app)
    }

    @Test
    fun commentsCountAndUnreadFromOthers() = runBlocking {
        val chat = Repo.createChat("Сім'я")
        val id = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Хліб", triggerAt = System.currentTimeMillis() + hour))
        Repo.addComment(id, "Куплю")
        // Моє повідомлення не «нове» для мене.
        assertEquals(0, Repo.commentStats(chat).first().single().unread)

        // Повідомлення від учасника спільного чату — нове, доки обговорення не відкрили.
        Repo.receiveComment(id, "Дякую!", authorName = "Мама", createdAt = System.currentTimeMillis() + 1)
        val stat = Repo.commentStats(chat).first().single()
        assertEquals(2, stat.count)
        assertEquals(1, stat.unread)

        Repo.markCommentsRead(id)
        assertEquals(0, Repo.commentStats(chat).first().single().unread)
    }

    @Test
    fun doneOneOffGoesToHistoryRepeatingMovesOn() = runBlocking {
        val chat = Repo.createChat("Дім")
        val at = System.currentTimeMillis() + hour
        val once = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Раз", triggerAt = at))
        val daily = Repo.createReminder(Reminder(chatId = chat, kind = Kind.TEXT, text = "Щодня", triggerAt = at, repeat = Repeat.DAILY))

        Repo.markDone(once)
        Repo.markDone(daily)

        val all = Repo.reminders(chat).first().associateBy { it.text }
        assertTrue(all.getValue("Раз").fired)
        val d = all.getValue("Щодня")
        assertTrue(!d.fired)
        assertEquals(Repo.nextOccurrence(at, Repeat.DAILY, at), d.triggerAt)
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals("лишився будильник тільки повторюваного", 1, alarms.size)
    }

    @Test
    fun tapExpandsOneCardAndCollapsesThePrevious() {
        val chat = runBlocking {
            val c = Repo.createChat("Дім")
            val now = System.currentTimeMillis()
            Repo.createReminder(Reminder(chatId = c, kind = Kind.TEXT, text = "Перше нагадування", triggerAt = now + hour))
            Repo.createReminder(Reminder(chatId = c, kind = Kind.TEXT, text = "Друге нагадування", triggerAt = now + 2 * hour))
            c
        }
        compose.setContent {
            NahadaikaTheme { ChatScreen(chatId = chat, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) }
        }
        compose.waitForIdle()
        // Згорнуті: дій не видно.
        assertEquals(0, compose.onAllNodesWithText("Готово").fetchSemanticsNodes().size)

        compose.onNodeWithText("Перше нагадування").performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("Готово").fetchSemanticsNodes().size)
        assertNotNull(compose.onNodeWithText("Обговорити…").fetchSemanticsNode())

        compose.onNodeWithText("Друге нагадування").performClick()
        compose.waitForIdle()
        // Розгорнута лише одна картка.
        assertEquals(1, compose.onAllNodesWithText("Готово").fetchSemanticsNodes().size)
    }
}
