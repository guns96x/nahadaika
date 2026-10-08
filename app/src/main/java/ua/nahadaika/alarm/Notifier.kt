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
import ua.nahadaika.Res
import ua.nahadaika.data.Chat
import ua.nahadaika.data.Comment
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.previewText
import ua.nahadaika.ui.MainActivity

object Notifier {
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, Res.s(R.string.core_channel_reminders_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Res.s(R.string.core_channel_reminders_desc)
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
        val channel = NotificationChannel(ALARM_CHANNEL_ID, Res.s(R.string.core_channel_alarms_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Res.s(R.string.core_channel_alarms_desc)
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
        val alarmTitle = body.ifBlank { Res.s(R.string.core_alarm_default_title) }
        val screen = PendingIntent.getActivity(
            context, id * 4,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlarmActivity.EXTRA_ID, reminder.id)
                .putExtra(AlarmActivity.EXTRA_TITLE, alarmTitle)
                .putExtra(AlarmActivity.EXTRA_CHAT, chatName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4A8FF.toInt())
            .setContentTitle(Res.s(R.string.core_alarm_notification_title, alarmTitle))
            .setContentText(chatName)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(10 * 60_000L)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(0, Res.s(R.string.core_alarm_snooze_5min), snoozeIntent(context, reminder.id, 5, id * 4 + 1))
            .addAction(0, Res.s(R.string.core_alarm_dismiss), doneIntent(context, reminder.id, id * 4 + 2))
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
            .addAction(0, Res.s(R.string.core_action_snooze_min, snooze), snoozeIntent(context, reminder.id, snooze.toLong(), id * 4 + 1))
            .addAction(0, Res.s(R.string.core_action_snooze_1hour), snoozeIntent(context, reminder.id, 60, id * 4 + 2))

        if (reminder.kind == Kind.VOICE) {
            builder.addAction(0, Res.s(R.string.core_action_listen), openIntent(context, reminder, autoplay = true, requestCode = id * 4 + 3))
        } else {
            builder.addAction(0, Res.s(R.string.core_action_done), doneIntent(context, reminder.id, id * 4 + 3))
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

    const val SHARED_CHANNEL_ID = "shared"

    /** Скільки останніх рядків показувати в згорнутому списку нового. */
    private const val MAX_LINES = 6

    private fun createSharedChannel(context: Context) {
        val channel = NotificationChannel(SHARED_CHANNEL_ID, Res.s(R.string.share_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = Res.s(R.string.share_channel_desc)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Нове від інших учасників спільного чату: одне сповіщення на чат (нові замінюють попереднє).
     * Ідентифікатор від'ємний, щоб не перетнутися зі сповіщеннями нагадувань (там id нагадування).
     */
    fun showIncoming(context: Context, chat: Chat, reminders: List<Reminder>, comments: List<Pair<Comment, Reminder?>>) {
        if (!canNotify(context) || (reminders.isEmpty() && comments.isEmpty())) return
        createSharedChannel(context)
        val fresh = reminders.map { Res.s(R.string.share_new_reminder, it.authorName.orEmpty(), previewText(it)) } +
            comments.map { (c, about) -> Res.s(R.string.share_new_comment, c.authorName.orEmpty(), about?.let(::previewText).orEmpty(), c.text) }
        val id = -(chat.id.toInt() + 1)
        // Зміни приходять порціями — дописуємо до ще не прочитаного сповіщення, а не затираємо його.
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id }?.notification
        val before = shown?.extras?.let { e ->
            e.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)?.map(CharSequence::toString)
                ?: e.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.let { listOf(it.toString()) }
        }.orEmpty()
        val lines = (before + fresh).takeLast(MAX_LINES)
        val count = (shown?.number?.takeIf { it > 0 } ?: before.size) + fresh.size
        val single = count == 1
        // Одне нагадування — відкрити саме його; кілька — просто чат.
        val focus = if (shown != null) -1L else reminders.singleOrNull()?.id ?: comments.map { it.first.reminderId }.distinct().singleOrNull() ?: -1L
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .setAction("ua.nahadaika.SHARED.${chat.id}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_CHAT_ID, chat.id)
                .putExtra(MainActivity.EXTRA_REMINDER_ID, focus),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, SHARED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB4A8FF.toInt())
            .setContentTitle(chat.name)
            .setContentText(if (single) lines.single() else Res.s(R.string.share_news_count, count))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setNumber(count)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(open)
        if (single) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(lines.single()))
        } else {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
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
