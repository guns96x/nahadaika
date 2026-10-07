package ua.nahadaika

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
import ua.nahadaika.ui.SearchScreen
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.ui.theme.ThemeMode
import ua.nahadaika.ui.theme.ThemeSettings
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

    // Темна тема — окремо від типової світлої, щоб знімки покривали обидві.
    @org.junit.Before
    fun darkByDefault() = ThemeSettings.set(app, ThemeMode.DARK)

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
            Reminder(
                chatId = home, kind = Kind.TEXT, text = "Вітамін D", triggerAt = at(1, 8), repeat = Repeat.DAILY,
                lastFiredAt = at(0, 8),
            ),
        )
        Repo.createReminder(
            Reminder(chatId = home, kind = Kind.VOICE, mediaPath = "/evening.m4a", durationMs = 9_000, text = "Що купити на вечерю", triggerAt = at(0, 20)),
        )
        Repo.createReminder(
            Reminder(chatId = home, kind = Kind.VIDEO, mediaPath = "/circle.mp4", durationMs = 7_000, triggerAt = at(0, 21, 30)),
        )
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
        compose.setContent { NahadaikaTheme { ChatsScreen(onOpenChat = {}, onBack = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/1_chats.png")
    }

    @Test
    fun chat() {
        val home = seed()
        compose.setContent { NahadaikaTheme { ChatScreen(chatId = home, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/2_chat.png")
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(5)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/2b_chat_bottom.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun schedule() {
        compose.setContent {
            NahadaikaTheme { ScheduleSheet(initialAt = null, initialRepeat = Repeat.NONE, confirmLabel = "Запланувати", onDismiss = {}) { _, _ -> } }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/3_schedule_at.png")
        compose.onNodeWithText("Через").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/4_schedule_in.png")
    }

    @Test
    fun chatLight() {
        val home = seed()
        ThemeSettings.set(app, ThemeMode.LIGHT)
        compose.setContent { NahadaikaTheme { ChatScreen(chatId = home, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/5_chat_light.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun scheduleLight() {
        ThemeSettings.set(app, ThemeMode.LIGHT)
        compose.setContent {
            NahadaikaTheme { ScheduleSheet(initialAt = null, initialRepeat = Repeat.NONE, confirmLabel = "Запланувати", onDismiss = {}) { _, _ -> } }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/6_schedule_light.png")
    }

    @Test
    fun chatsLight() {
        seed()
        ThemeSettings.set(app, ThemeMode.LIGHT)
        compose.setContent { NahadaikaTheme { ChatsScreen(onOpenChat = {}, onBack = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/7_chats_light.png")
    }

    /** Порожній чат зі збільшеним шрифтом системи — як на реальному телефоні. */
    @Test
    fun emptyLargeFont() {
        RuntimeEnvironment.setFontScale(1.15f)
        val chatId = runBlocking { Repo.init(app); Repo.createChat("Нагадування") }
        compose.setContent { NahadaikaTheme { ChatScreen(chatId = chatId, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/8_empty_large_font.png")
    }

    /** Пошук по всіх чатах: усі, пошук за словом і архів виконаних. */
    @Test
    fun search() {
        val home = seed()
        runBlocking {
            Repo.createReminder(
                Reminder(chatId = home, kind = Kind.TEXT, text = "Зателефонувати лікарю", triggerAt = at(-1, 9), fired = true, lastFiredAt = at(-1, 9)),
            )
        }
        compose.setContent { NahadaikaTheme { SearchScreen(onBack = {}, onOpen = { _, _ -> }) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/9_search_all.png")
        compose.onNode(hasSetTextAction()).performTextInput("вітамін")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/9b_search_query.png")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Виконані").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/9c_search_done.png")
    }

    /** Акордеон: згорнуті заголовки, одна розгорнута картка з діями й обговоренням. */
    @Test
    fun chatExpanded() {
        val home = seed()
        runBlocking {
            val parcel = Repo.reminders(home).first().first { it.text.startsWith("Забрати посилку") }
            Repo.addComment(parcel.id, "Візьми паспорт")
            Repo.receiveComment(parcel.id, "Добре, після роботи заберу", authorName = "Оля")
        }
        compose.setContent { NahadaikaTheme { ChatScreen(chatId = home, focus = null, onFocusConsumed = {}, quick = null, onQuickConsumed = {}, onOpenChats = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/13_chat_collapsed.png")
        compose.onNodeWithText("Забрати посилку на пошті 📦").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/13b_chat_expanded.png")
    }
}
