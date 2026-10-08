package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import java.time.LocalDate
import java.time.ZoneId

/** Дні гортаються пальцем уліво-вправо, як сторінки. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h900dp-xxhdpi")
class DaySwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Prefs.setGestureHintShown(app)
        Repo.init(app)
    }

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun swipingChangesDay() {
        val today = LocalDate.now()
        val chat = runBlocking {
            val c = Repo.createChat("Дім")
            Repo.createReminder(Reminder(chatId = c, kind = Kind.TEXT, text = "Сьогоднішнє", triggerAt = maxOf(at(today, 23), System.currentTimeMillis() + 60_000)))
            Repo.createReminder(Reminder(chatId = c, kind = Kind.TEXT, text = "Завтрашнє", triggerAt = at(today.plusDays(1), 12)))
            c
        }
        compose.setContent {
            NahadaikaTheme { ChatScreen(chatId = chat, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Сьогоднішнє").assertIsDisplayed()

        // Уліво — наступний день.
        compose.onNodeWithText("Сьогоднішнє").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Завтрашнє").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Сьогоднішнє").fetchSemanticsNodes().size)

        // Управо — назад.
        compose.onNodeWithText("Завтрашнє").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Сьогоднішнє").assertIsDisplayed()
    }
}
