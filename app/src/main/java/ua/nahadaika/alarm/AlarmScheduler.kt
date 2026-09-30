package ua.nahadaika.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.alarmAt
import ua.nahadaika.ui.MainActivity

object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminder.id)
        val at = reminder.alarmAt()
        if (reminder.alarm && canScheduleExact(context)) {
            // Як справжній будильник: значок у рядку стану, спрацьовує навіть у режимі енергозбереження.
            val show = PendingIntent.getActivity(
                context, reminder.id.toInt(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Без дозволу на точні будильники — хоча б приблизно вчасно.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
