package ua.nahadaika

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.media.MediaFiles
import ua.nahadaika.ui.ChatScreen
import ua.nahadaika.ui.ChatsScreen
import ua.nahadaika.ui.ScheduleSheet
import ua.nahadaika.ui.theme.NahadaikaTheme
import java.time.LocalDate
import java.time.ZoneId

/** Знімки екранів для перевірки вигляду (пишуться в app/build/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun at(daysFromToday: Long, hour: Int, minute: Int = 0) =
        LocalDate.now().plusDays(daysFromToday).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun samplePhoto(): String {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 800f, 600f, 0xFFFFB74D.toInt(), 0xFF4FC3F7.toInt(), Shader.TileMode.CLAMP)
        })
        val file = MediaFiles.newFile(app, "jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    private fun seed(): Long = runBlocking {
        Repo.init(app)
        val home = Repo.createChat("Дім")
        val work = Repo.createChat("Робота")
        val meds = Repo.createChat("Ліки")
        Repo.createReminder(Reminder(chatId = home, kind = Kind.TEXT, text = "Забрати посилку на пошті 📦", triggerAt = at(0, 18, 30)))
        Repo.createReminder(
            Reminder(chatId = home, kind = Kind.VOICE, mediaPath = "/voice.m4a", durationMs = 14_000, triggerAt = at(1, 9)),
        )
        Repo.createReminder(
            Reminder(chatId = home, kind = Kind.PHOTO, mediaPath = samplePhoto(), text = "Купити саме такий торт 🎂", triggerAt = at(3, 12)),
        )
        Repo.createReminder(
            Reminder(chatId = home, kind = Kind.TEXT, text = "Оплатити комуналку", triggerAt = at(5, 10), repeat = Repeat.MONTHLY),
        )
        Repo.createReminder(Reminder(chatId = work, kind = Kind.TEXT, text = "Звіт до п'ятниці", triggerAt = at(2, 11)))
        Repo.createReminder(
            Reminder(chatId = meds, kind = Kind.TEXT, text = "Вітамін D", triggerAt = at(1, 8), repeat = Repeat.DAILY),
        )
        home
    }

    @Test
    fun chats() {
        seed()
        compose.setContent { NahadaikaTheme { ChatsScreen(onOpenChat = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/1_chats.png")
    }

    @Test
    fun chat() {
        val home = seed()
        compose.setContent { NahadaikaTheme { ChatScreen(chatId = home, focus = null, onFocusConsumed = {}, onBack = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/2_chat.png")
    }

    @Test
    fun schedule() {
        compose.setContent {
            NahadaikaTheme { ScheduleSheet(initialAt = null, initialRepeat = Repeat.NONE, confirmLabel = "Запланувати", onDismiss = {}) { _, _ -> } }
        }
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screenshots/3_schedule.png")
    }
}
