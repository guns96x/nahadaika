package ua.nahadaika

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.ui.SettingsScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.update.UpdateAsset
import ua.nahadaika.update.UpdateInfo
import ua.nahadaika.update.Updates

/** Розділ «Оновлення» в налаштуваннях — лише варіант github. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h1400dp-xxhdpi")
class UpdateSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show() {
        compose.setContent { NahadaikaTheme { SettingsScreen(onBack = {}) } }
        compose.waitForIdle()
    }

    @Test
    fun autoUpdateToggleIsSaved() {
        show()
        compose.onAllNodes(isToggleable())[0].assertIsOn().performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        assertFalse(Prefs.autoUpdate(app))
    }

    @Test
    fun showsAvailableUpdate() {
        Updates.state = Updates.State.Available(
            UpdateInfo(
                versionCode = 99, versionName = "3.7", notes = "• Будильник, що справді дзвонить",
                apk = UpdateAsset("https://x/apk", 20_600_000, "aa"),
                patch = UpdateAsset("https://x/patch", 1_800_000, "bb"), patchFromSha256 = "cc",
            ),
        )
        show()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Оновити"))
        compose.onNodeWithText("Доступна версія 3.7").assertExists()
        compose.onNodeWithText("Завантажити 1,7 МБ замість 19,6 МБ").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/12_update_available.png")
        Updates.state = Updates.State.Idle
    }
}
