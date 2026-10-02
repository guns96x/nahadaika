package ua.nahadaika

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme

/** Відео відтворюється прямо в бульбашці, а не в окремому вікні. */
@RunWith(RobolectricTestRunner::class)
// Справжня графіка: без неї Robolectric не вміє перевіряти влучання в бульбашку з різними радіусами кутів.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xxhdpi")
class InlineVideoTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun tapPlaysVideoInsideBubble() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Фейкове 9-секундне відео для MediaPlayer у Robolectric.
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource("/nope.mp4"), ShadowMediaPlayer.MediaInfo(9_000, 0))
        Prefs.setGestureHintShown(app)
        val chatId = runBlocking {
            Repo.init(app)
            val id = Repo.createChat("Дім")
            Repo.createReminder(
                Reminder(
                    chatId = id, kind = Kind.VIDEO, mediaPath = "/nope.mp4", durationMs = 9_000,
                    triggerAt = System.currentTimeMillis() + 3_600_000,
                ),
            )
            id
        }
        compose.setContent {
            NahadaikaTheme {
                ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {})
            }
        }
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Відтворити відео").performClick()
        compose.mainClock.advanceTimeBy(500)

        // Плеєр з'явився в самій бульбашці, без окремого вікна; кнопки «відтворити» більше немає.
        compose.onNodeWithContentDescription("Відео відтворюється").assertExists()
        assertEquals(0, compose.onAllNodesWithContentDescription("Відтворити відео").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)
    }

    @Test
    fun fullscreenButtonOpensAndClosesViewer() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource("/nope.mp4"), ShadowMediaPlayer.MediaInfo(9_000, 0))
        Prefs.setGestureHintShown(app)
        val chatId = runBlocking {
            Repo.init(app)
            val id = Repo.createChat("Дім")
            Repo.createReminder(
                Reminder(
                    chatId = id, kind = Kind.VIDEO, mediaPath = "/nope.mp4", durationMs = 9_000,
                    triggerAt = System.currentTimeMillis() + 3_600_000,
                ),
            )
            id
        }
        compose.setContent {
            NahadaikaTheme {
                ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("На весь екран").performClick()
        compose.waitForIdle()
        // Повноекранний перегляд — окреме вікно (діалог) з кнопкою «Закрити».
        assertEquals(2, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Закрити").performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(isRoot()).fetchSemanticsNodes().size)
    }
}
