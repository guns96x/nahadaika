package ua.nahadaika

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.ui.MainActivity
import ua.nahadaika.widget.ReminderWidget

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun inflate(next: Reminder?, chat: String?): View =
        ReminderWidget.buildViews(app, next, chat).apply(app, FrameLayout(app))

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test fun showsNextReminderWithChatName() {
        val r = Reminder(id = 7, chatId = 3, kind = Kind.TEXT, text = "Купити хліб", triggerAt = System.currentTimeMillis() + 3_600_000)
        val view = inflate(r, "Дім")
        assertEquals("Купити хліб", view.text(R.id.widget_text))
        assertTrue(view.text(R.id.widget_when).endsWith("· Дім"))
    }

    @Test fun showsPlaceholderWithoutReminders() {
        val view = inflate(null, null)
        assertEquals("Немає запланованих нагадувань", view.text(R.id.widget_text))
    }

    @Test fun buttonsOpenAppWithQuickActions() {
        val view = inflate(null, null)
        val expected = mapOf(
            R.id.widget_dictate to MainActivity.ACTION_QUICK_DICTATE,
            R.id.widget_voice to MainActivity.ACTION_QUICK_VOICE,
            R.id.widget_video to MainActivity.ACTION_QUICK_VIDEO,
        )
        expected.forEach { (id, action) ->
            view.findViewById<View>(id).performClick()
            val started = shadowOf(app).nextStartedActivity
            assertEquals(action, started.action)
            assertEquals(MainActivity::class.java.name, started.component?.className)
        }
    }

    @Test fun tapOnReminderOpensItsChat() {
        val r = Reminder(id = 7, chatId = 3, kind = Kind.TEXT, text = "Хліб", triggerAt = System.currentTimeMillis() + 60_000)
        inflate(r, "Дім").findViewById<View>(R.id.widget_open).performClick()
        val started = shadowOf(app).nextStartedActivity
        assertEquals(3L, started.getLongExtra(MainActivity.EXTRA_CHAT_ID, -1))
        assertEquals(7L, started.getLongExtra(MainActivity.EXTRA_REMINDER_ID, -1))
    }
}
