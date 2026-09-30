package ua.nahadaika.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.nahadaika.Prefs
import ua.nahadaika.R
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Фонова перевірка оновлень раз на пів дня; про нову версію — одне сповіщення. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val info = runCatching { Updates.fetch(applicationContext) }.getOrNull() ?: return Result.success()
        Prefs.setLastUpdateCheck(applicationContext, System.currentTimeMillis())
        if (Prefs.notifiedUpdate(applicationContext) != info.versionCode) {
            notify(applicationContext, info)
            Prefs.setNotifiedUpdate(applicationContext, info.versionCode)
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "update-check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 777_001

        fun schedule(context: Context, enabled: Boolean) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, info: UpdateInfo) {
            if (!Notifier.canNotify(context)) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Оновлення застосунку", NotificationManager.IMPORTANCE_DEFAULT),
            )
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_UPDATES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val size = "%.1f".format(info.downloadSize / 1048576f).replace('.', ',')
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Доступна Нагадайка ${info.versionName}")
                .setContentText("Натисніть, щоб оновити — завантажити $size МБ")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        }
    }
}
