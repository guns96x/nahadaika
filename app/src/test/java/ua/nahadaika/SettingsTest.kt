package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Kind
import ua.nahadaika.ui.SettingsScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.ui.theme.ThemeMode
import ua.nahadaika.ui.theme.ThemeSettings

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1400dp-xxhdpi")
class SettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent { NahadaikaTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    @Test
    fun changesAreSaved() {
        show()
        compose.onNodeWithText("15 хв").performClick()
        assertEquals(15, Prefs.snoozeMinutes(app))
        compose.onNodeWithText("8:00").performClick()
        assertEquals(8, Prefs.defaultHour(app))
        compose.onNodeWithText("Голосове").performClick()
        assertEquals(Kind.VOICE, Prefs.recordMode(app))
        assertEquals(ThemeMode.LIGHT, ThemeSettings.mode) // типова тема — світла
        compose.onNodeWithText("Темна").performClick()
        assertEquals(ThemeMode.DARK, ThemeSettings.mode)
    }

    @Test
    fun showsSettingsScreenshots() {
        ThemeSettings.set(app, ThemeMode.DARK)
        show()
        compose.onRoot().captureRoboImage("build/screenshots/11_settings_dark.png")
        ThemeSettings.set(app, ThemeMode.LIGHT)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/11_settings_light.png")
        ThemeSettings.set(app, ThemeMode.DARK)
    }
}
