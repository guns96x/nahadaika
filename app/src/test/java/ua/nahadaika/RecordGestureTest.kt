package ua.nahadaika

import android.Manifest
import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme

/** Жести кнопки запису, як у Telegram: тап — режим, утримання — запис, угору — замок, уліво — скасувати. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h860dp-xxhdpi")
class RecordGestureTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        Prefs.setGestureHintShown(app)
        Prefs.setRecordMode(app, Kind.VOICE)
        val chatId = runBlocking {
            Repo.init(app)
            Repo.createChat("Тест")
        }
        compose.setContent {
            NahadaikaTheme {
                ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {})
            }
        }
        compose.waitForIdle()
    }

    private fun voiceButton() = compose.onNodeWithContentDescription("Голосове", substring = true)

    @Test
    fun tapTogglesVoiceAndVideo() {
        voiceButton().performTouchInput { click() }
        compose.onNodeWithContentDescription("Відео", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Відео", substring = true).performTouchInput { click() }
        voiceButton().assertIsDisplayed()
    }

    @Test
    fun holdShowsRecordingAndSlideUpLocks() {
        voiceButton().performTouchInput {
            down(center)
            advanceEventTime(400)
            moveBy(Offset(0f, -10f))
        }
        compose.onNodeWithText("Посуньте, щоб скасувати").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/9_voice_holding.png")
        compose.onNodeWithContentDescription("Голосове", substring = true).performTouchInput {
            moveBy(Offset(0f, -400f))
            up()
        }
        // Замкнено: запис триває без утримання, є «Скасувати» і кнопка «Готово».
        compose.onNodeWithText("Скасувати").assertIsDisplayed()
        compose.onNodeWithContentDescription("Готово").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/9_voice_locked.png")
    }

    @Test
    fun slideLeftCancels() {
        voiceButton().performTouchInput {
            down(center)
            advanceEventTime(400)
            moveBy(Offset(-10f, 0f))
            moveBy(Offset(-600f, 0f))
            up()
        }
        compose.onNodeWithText("Нагадування").assertIsDisplayed()
        voiceButton().assertIsDisplayed()
    }

    @Test
    fun holdAndReleaseFinishesRecording() {
        voiceButton().performTouchInput {
            down(center)
            advanceEventTime(400)
            moveBy(Offset(0f, -5f))
            up()
        }
        // Запис завершено — знову звичайне поле вводу.
        compose.onNodeWithText("Нагадування").assertIsDisplayed()
    }
}
