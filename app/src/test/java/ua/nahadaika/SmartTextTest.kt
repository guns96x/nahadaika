package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.voice.SmartVoice
import ua.nahadaika.voice.VoiceContext
import ua.nahadaika.voice.VoiceDraft
import ua.nahadaika.voice.VoiceInterpreter
import ua.nahadaika.voice.VoiceOutcome
import ua.nahadaika.voice.VoiceResult
import java.io.File

/**
 * Одна кнопка: текст (надрукований чи надиктований мікрофоном клавіатури) після ⏰ іде в Gemini —
 * є час — нагадування ставиться саме, немає — відкривається вибір часу з уже очищеним текстом.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h900dp-xxhdpi")
class SmartTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tomorrow9 = System.currentTimeMillis() + 23 * 3_600_000L
    private var sent: String? = null

    /** Gemini-заглушка: «хліб» — з часом, усе інше — без часу. */
    private val fake = object : VoiceInterpreter {
        override suspend fun interpret(audio: File, mime: String, context: VoiceContext) = VoiceOutcome.Failed("not used")

        override suspend fun interpretText(text: String, context: VoiceContext): VoiceOutcome {
            sent = text
            val at = if ("хліб" in text) tomorrow9 else null
            return VoiceOutcome.Success(VoiceResult(text, listOf(VoiceDraft(if (at != null) "Купити хліб" else "Подзвонити мамі", at, Repeat.NONE, false))))
        }
    }

    @Before fun setUp() {
        Prefs.setGestureHintShown(app)
        Prefs.setSmartVoice(app, true)
        Repo.init(app)
        SmartVoice.interpreter = fake
    }

    @After fun tearDown() {
        SmartVoice.interpreter = null
    }

    private fun open(): Long {
        val chat = runBlocking { Repo.createChat("Дім") }
        compose.setContent {
            NahadaikaTheme { ChatScreen(chatId = chat, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) }
        }
        compose.waitForIdle()
        return chat
    }

    private fun typeAndSend(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запланувати").performTouchInput { click() }
        compose.waitUntil(5_000) { sent != null }
        compose.waitForIdle()
    }

    @Test fun textWithTimeIsScheduledWithoutPicker() {
        val chat = open()
        typeAndSend("завтра о 9 купити хліб")
        assertEquals("завтра о 9 купити хліб", sent)
        compose.waitUntil(5_000) { runBlocking { Repo.reminders(chat).first().isNotEmpty() } }
        val r = runBlocking { Repo.reminders(chat).first() }.single()
        assertEquals("Купити хліб", r.text)
        assertEquals(tomorrow9, r.triggerAt)
        assertEquals("вікна вибору часу немає", 0, compose.onAllNodesWithText("Коли нагадати?").fetchSemanticsNodes().size)
    }

    @Test fun textWithoutTimeOpensPickerWithCleanText() {
        val chat = open()
        typeAndSend("подзвонити мамі")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Коли нагадати?").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { Repo.reminders(chat).first() }.isEmpty())
        assertEquals(1, compose.onAllNodesWithText("Подзвонити мамі").fetchSemanticsNodes().size)
    }
}
