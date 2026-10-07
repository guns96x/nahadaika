package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.voice.SmartVoice
import ua.nahadaika.voice.VoiceContext
import ua.nahadaika.voice.VoiceInterpreter
import ua.nahadaika.voice.VoiceOutcome
import ua.nahadaika.voice.VoiceResult
import java.io.File

/** Кнопка «Сказати» (як мікрофон клавіатури) є лише там, де працює «розумний час». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h900dp-xxhdpi")
class DictationUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        Prefs.setGestureHintShown(app)
        Repo.init(app)
    }

    @After fun tearDown() {
        SmartVoice.interpreter = null
    }

    private fun showChat() {
        val chat = runBlocking { Repo.createChat("Дім") }
        compose.setContent {
            NahadaikaTheme { ChatScreen(chatId = chat, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) }
        }
        compose.waitForIdle()
    }

    private fun dictateButtons() = compose.onAllNodesWithContentDescription("Сказати нагадування").fetchSemanticsNodes().size

    @Test fun noButtonWithoutSmartTime() {
        showChat()
        assertEquals(0, dictateButtons())
    }

    @Test fun buttonAppearsWhenSmartTimeIsAvailable() {
        SmartVoice.interpreter = object : VoiceInterpreter {
            override suspend fun interpret(audio: File, mime: String, context: VoiceContext) = VoiceOutcome.Success(VoiceResult("", emptyList()))
        }
        showChat()
        assertEquals(1, dictateButtons())
    }
}
