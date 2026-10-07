package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Repeat
import ua.nahadaika.ui.SettingsScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import java.time.LocalDate

/** Англійський інтерфейс: переклад повний (без кирилиці), формати часу й дат — англійські. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w400dp-h1400dp-xxhdpi")
class LocalizationTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cyrillic = Regex("[А-Яа-яІіЇїЄєҐґ]")

    @Test
    fun everyStringIsTranslated() {
        val leaked = R.string::class.java.fields.mapNotNull { f ->
            val id = f.getInt(null)
            val text = app.getString(id)
            f.name.takeIf { cyrillic.containsMatchIn(text) }
        }.filterNot { it in SAME_IN_ALL_LANGUAGES }
        assertTrue("Не перекладено: $leaked", leaked.isEmpty())
    }

    @Test
    fun timeLabelsAreEnglish() {
        assertEquals("Daily", repeatLabel(Repeat.DAILY))
        assertEquals("Today", dayLabel(LocalDate.now()))
        assertEquals("Tomorrow", dayLabel(LocalDate.now().plusDays(1)))
        assertEquals("in 1 h 17 min", inLabel(77 * 60_000L))
        assertEquals("in 25 min", soonLabel(System.currentTimeMillis() + 25 * 60_000L))
        assertFalse(cyrillic.containsMatchIn(shortDate(LocalDate.of(2026, 10, 7))))
        assertEquals("Wed, Oct 7", shortDate(LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun settingsScreenInEnglish() {
        compose.setContent { NahadaikaTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Default time").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/14_settings_en.png")
    }

    private companion object {
        /** Ресурси, що однакові в усіх мовах і не мають бути перекладені. */
        val SAME_IN_ALL_LANGUAGES = emptySet<String>()
    }
}
