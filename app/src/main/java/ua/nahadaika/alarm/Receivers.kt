package ua.nahadaika.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.nahadaika.data.Repo

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Спрацювання будильника та кнопки у сповіщенні. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        when (intent.action) {
            ACTION_FIRE -> runAsync { Repo.deliver(id) }
            ACTION_SNOOZE -> {
                val minutes = intent.getLongExtra(EXTRA_MINUTES, 10)
                runAsync { Repo.snooze(id, minutes) }
            }
            ACTION_DONE -> Notifier.cancel(context, id)
        }
    }

    companion object {
        const val ACTION_FIRE = "ua.nahadaika.FIRE"
        const val ACTION_SNOOZE = "ua.nahadaika.SNOOZE"
        const val ACTION_DONE = "ua.nahadaika.DONE"
        const val EXTRA_ID = "id"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Будильники не переживають перезавантаження — ставимо їх заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        Repo.init(context)
        runAsync { Repo.rescheduleAll() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
