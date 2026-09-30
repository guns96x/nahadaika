package ua.nahadaika.alarm

import ua.nahadaika.Prefs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ua.nahadaika.R
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.previewText
import ua.nahadaika.ui.MainActivity

object Notifier {
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Нагадування", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Сповіщення про заплановані нагадування"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    const val ALARM_CHANNEL_ID = "alarms"

    private fun createAlarmChannel(context: Context) {
        val channel = NotificationChannel(ALARM_CHANNEL_ID, "Будильники й таймери", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Гучна мелодія, поки не вимкнете"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Чи може будильник відкриватися на весь екран (Android 14+ це окремий дозвіл). */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Будильник: мелодія повторюється (до 10 хв), екран на весь дисплей, «Вимкнути» / «Ще 5 хв». */
    private fun showAlarm(context: Context, reminder: Reminder, chatName: String, body: String) {
        createAlarmChannel(context)
        val id = reminder.id.toInt()
        val screen = PendingIntent.getActivity(
            context, id * 4,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlarmActivity.EXTRA_ID, reminder.id)
                .putExtra(AlarmActivity.EXTRA_TITLE, body.ifBlank { "Будильник" })
                .putExtra(AlarmActivity.EXTRA_CHAT, chatName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4A8FF.toInt())
            .setContentTitle("⏰ ${body.ifBlank { "Будильник" }}")
            .setContentText(chatName)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(10 * 60_000L)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(0, "Ще 5 хв", snoozeIntent(context, reminder.id, 5, id * 4 + 1))
            .addAction(0, "Вимкнути", doneIntent(context, reminder.id, id * 4 + 2))
            .build()
            .apply { flags = flags or android.app.Notification.FLAG_INSISTENT }
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, reminder: Reminder, chatName: String) {
        if (!canNotify(context)) return
        val id = reminder.id.toInt()

        val body = when (reminder.kind) {
            Kind.TEXT -> reminder.text
            else -> previewText(reminder)
        }

        if (reminder.alarm) {
            showAlarm(context, reminder, chatName, body)
            return
        }

        val snooze = Prefs.snoozeMinutes(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4A8FF.toInt())
            .setContentTitle(chatName)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openIntent(context, reminder, autoplay = false, requestCode = id * 4))
            .addAction(0, "+$snooze хв", snoozeIntent(context, reminder.id, snooze.toLong(), id * 4 + 1))
            .addAction(0, "+1 год", snoozeIntent(context, reminder.id, 60, id * 4 + 2))

        if (reminder.kind == Kind.VOICE) {
            builder.addAction(0, "▶ Слухати", openIntent(context, reminder, autoplay = true, requestCode = id * 4 + 3))
        } else {
            builder.addAction(0, "Готово", doneIntent(context, reminder.id, id * 4 + 3))
        }

        val picture = when (reminder.kind) {
            Kind.PHOTO -> reminder.mediaPath?.let { decodeSampled(it) }
            Kind.VIDEO -> reminder.mediaPath?.let { videoFrame(it) }
            else -> null
        }
        if (picture != null) {
            builder.setLargeIcon(picture)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(body),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Дозвіл відкликали між перевіркою та показом.
        }
    }

    fun cancel(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }

    private fun openIntent(context: Context, r: Reminder, autoplay: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction("ua.nahadaika.OPEN.${r.id}.$autoplay")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_CHAT_ID, r.chatId)
            .putExtra(MainActivity.EXTRA_REMINDER_ID, r.id)
            .putExtra(MainActivity.EXTRA_AUTOPLAY, autoplay)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeIntent(context: Context, id: Long, minutes: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_SNOOZE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun doneIntent(context: Context, id: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_DONE)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun decodeSampled(path: String, maxSide: Int = 1024): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    }

    private fun videoFrame(path: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                val scale = 1024f / maxOf(frame.width, frame.height)
                if (scale >= 1f) frame
                else Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
