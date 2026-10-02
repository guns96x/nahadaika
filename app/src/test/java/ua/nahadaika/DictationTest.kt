package ua.nahadaika

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.IntentFilter
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSpeechRecognizer
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.theme.NahadaikaTheme

/** Голосова команда слухається прямо в полі вводу — без вікна Google — і одразу планує нагадування. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class DictationTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun dictationWithoutDialogSchedulesReminder() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        Prefs.setGestureHintShown(app)
        // Системний розпізнавач мовлення, як на справжньому телефоні.
        val service = ComponentName("com.google.android.tts", "com.google.Recognizer")
        shadowOf(app.packageManager).apply {
            addServiceIfNotPresent(service)
            addIntentFilterForService(service, IntentFilter(RecognitionService.SERVICE_INTERFACE))
        }
        val chatId = runBlocking {
            Repo.init(app)
            Repo.createChat("Дім")
        }
        compose.setContent {
            NahadaikaTheme {
                ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Сказати нагадування").performClick()
        compose.onNodeWithText("Слухаю", substring = true).assertIsDisplayed()

        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        recognizer.triggerOnPartialResults(results("завтра о 9 купити"))
        compose.onNodeWithText("завтра о 9 купити").assertIsDisplayed()
        compose.onNodeWithContentDescription("Готово").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/screenshots/10_dictation.png")

        recognizer.triggerOnResults(results("нагадай завтра о 9 купити хліб"))
        compose.waitUntil(5_000) { runBlocking { Repo.reminders(chatId).first() }.isNotEmpty() }

        val saved = runBlocking { Repo.reminders(chatId).first() }
        assertEquals(1, saved.size)
        assertEquals("Купити хліб", saved[0].text)
        compose.onNodeWithText("Нагадування").assertIsDisplayed()
    }

    @Test
    fun severalRemindersFromOnePhrase() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        Prefs.setGestureHintShown(app)
        val service = ComponentName("com.google.android.tts", "com.google.Recognizer")
        shadowOf(app.packageManager).apply {
            addServiceIfNotPresent(service)
            addIntentFilterForService(service, IntentFilter(RecognitionService.SERVICE_INTERFACE))
        }
        val chatId = runBlocking {
            Repo.init(app)
            Repo.createChat("Дім")
        }
        compose.setContent {
            NahadaikaTheme {
                ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Сказати нагадування").performClick()
        compose.waitForIdle()
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            .triggerOnResults(results("завтра о 9 купити хліб, о 12 подзвонити в банк, а в п'ятницю о 18 кіно"))
        compose.waitUntil(5_000) { runBlocking { Repo.reminders(chatId).first() }.size == 3 }
        val saved = runBlocking { Repo.reminders(chatId).first() }.sortedBy { it.triggerAt }
        assertEquals(listOf("Купити хліб", "Подзвонити в банк", "Кіно"), saved.map { it.text })
        // Підтвердження з'являється вже після створення останнього нагадування — чекаємо, а не перевіряємо миттєво.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Поставив 3 нагадування", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }
}
