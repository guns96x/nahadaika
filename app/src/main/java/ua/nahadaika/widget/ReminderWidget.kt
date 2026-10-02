package ua.nahadaika.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.nahadaika.R
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repo
import ua.nahadaika.data.alarmAt
import ua.nahadaika.previewText
import ua.nahadaika.ui.MainActivity
import ua.nahadaika.whenLabel

/** Віджет на головному екрані: найближче нагадування, тап — відкрити його, три кнопки швидкого запису. */
class ReminderWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        Repo.init(context)
        Repo.watchWidget()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val next = Repo.nextPending()
                appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context, next?.first, next?.second)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun isPlaced(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ReminderWidget::class.java))

        /** Перемалювати всі віджети після зміни нагадувань; без віджетів нічого не робить. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = ids(context)
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val next = Repo.nextPending()
                ids.forEach { manager.updateAppWidget(it, buildViews(context, next?.first, next?.second)) }
            }
        }

        internal fun buildViews(context: Context, next: Reminder?, chatName: String?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_reminder)
            if (next == null) {
                views.setTextViewText(R.id.widget_when, "Нагадайка")
                views.setTextViewText(R.id.widget_text, "Немає запланованих нагадувань")
            } else {
                views.setTextViewText(R.id.widget_when, listOfNotNull(whenLabel(next.alarmAt()), chatName).joinToString(" · "))
                views.setTextViewText(R.id.widget_text, previewText(next).ifBlank { "Нагадування" })
            }
            val open = Intent(context, MainActivity::class.java).apply {
                if (next != null) {
                    putExtra(MainActivity.EXTRA_CHAT_ID, next.chatId)
                    putExtra(MainActivity.EXTRA_REMINDER_ID, next.id)
                }
            }
            views.setOnClickPendingIntent(R.id.widget_open, activity(context, 0, open))
            views.setOnClickPendingIntent(R.id.widget_dictate, quick(context, 1, MainActivity.ACTION_QUICK_DICTATE))
            views.setOnClickPendingIntent(R.id.widget_voice, quick(context, 2, MainActivity.ACTION_QUICK_VOICE))
            views.setOnClickPendingIntent(R.id.widget_video, quick(context, 3, MainActivity.ACTION_QUICK_VIDEO))
            return views
        }

        private fun quick(context: Context, code: Int, action: String) =
            activity(context, code, Intent(context, MainActivity::class.java).setAction(action))

        private fun activity(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
