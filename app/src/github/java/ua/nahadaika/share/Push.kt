package ua.nahadaika.share

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import ua.nahadaika.BuildConfig
import ua.nahadaika.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Миттєві сповіщення про зміни в спільних чатах. Телефон кладе свій FCM-токен у chats/{chat}/push/{uid}_{пристрій}
 * (читають лише учасники), а після запису кличе сервер сповіщень (Supabase Edge Function, supabase/functions/notify).
 * Сервер перевіряє вхід і членство в чаті токеном самого користувача й шле іншим data-повідомлення FCM.
 */
internal class PushLink(context: Context, private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    private suspend fun <T> Task<T>.confirmed(): T = withTimeout(20_000) { await() }

    /** Свій для кожного встановлення: у одного акаунта може бути кілька телефонів. */
    private val device: String by lazy {
        prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    }

    companion object {
        /** Перша версія, що вміє миттєві сповіщення й медіа «кур'єром» (3.17). Старіші цього не знають. */
        const val MIN_VERSION = 26
    }

    private suspend fun token(): String? = runCatching { FirebaseMessaging.getInstance().token.confirmed() }.getOrNull()

    private fun doc(chatId: String, uid: String) = db.collection("chats").document(chatId).collection("push").document("${uid}_$device")

    suspend fun register(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        val token = token() ?: return
        val stamp = "$uid|$token|${BuildConfig.VERSION_CODE}"
        if (prefs.getString("chat_$chatId", null) == stamp) return
        doc(chatId, uid).set(
            mapOf("uid" to uid, "token" to token, "updatedAt" to FieldValue.serverTimestamp(), "app" to BuildConfig.VERSION_CODE),
        ).confirmed()
        prefs.edit().putString("chat_$chatId", stamp).apply()
    }

    suspend fun unregister(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        prefs.edit().remove("chat_$chatId").apply()
        doc(chatId, uid).delete().confirmed()
    }

    /** Попросити сервер надіслати лист за запрошенням mailInvites/{id}; true — сервер відповів 200. */
    suspend fun sendInviteMail(inviteId: String): Boolean {
        if (BuildConfig.PUSH_URL.isBlank()) return false
        val idToken = auth.currentUser?.getIdToken(false)?.confirmed()?.token ?: return false
        val body = JSONObject().put("invite", inviteId).toString()
        return withContext(Dispatchers.IO) {
            val conn = URL(BuildConfig.PUSH_URL.replace("/notify", "/invite-mail")).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $idToken")
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun notify(chatId: String, urgent: Boolean) {
        if (BuildConfig.PUSH_URL.isBlank()) return
        val idToken = auth.currentUser?.getIdToken(false)?.confirmed()?.token ?: return
        // Свій токен — щоб сервер не будив цей же телефон.
        val body = JSONObject().put("chat", chatId).put("urgent", urgent).put("from", token().orEmpty()).toString()
        withContext(Dispatchers.IO) {
            val conn = URL(BuildConfig.PUSH_URL).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $idToken")
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** Приймає FCM: новий токен — перереєструватися; повідомлення — забрати зміни чату. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        SharedChats.onPushToken()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val chat = message.data["chat"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: return
        PushSyncWorker.lastPush[chat] = System.currentTimeMillis()
        PushSyncWorker.enqueue(this, chat)
    }
}

/**
 * Синхронізація чату після push-сповіщення. Термінова робота: високопріоритетне FCM дає застосунку право
 * запуститися з фону. Якщо квоту вичерпано — звичайна робота; запасний варіант — SharedSyncWorker раз на 2 год.
 */
class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val chat = inputData.getString(KEY_CHAT) ?: return Result.success()
        return runCatching {
            // Сповіщення, що прийшли до початку попередньої синхронізації, вона вже врахувала — не читати чат удруге.
            // Інакше — не частіше за раз на 15 с: навіть 30 сповіщень на хвилину не з'їдять квоту читань Firestore.
            if ((lastPush[chat] ?: Long.MAX_VALUE) < (lastSync[chat] ?: 0)) return@runCatching
            delay((MIN_GAP_MS - (System.currentTimeMillis() - (lastSync[chat] ?: 0))).coerceIn(0, MIN_GAP_MS))
            val started = System.currentTimeMillis()
            SharedChats.syncChat(chat)
            lastSync[chat] = started
        }.fold(
            { Result.success() },
            { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
    }

    /** До Android 12 термінова робота йде як foreground-сервіс і потребує сповіщення. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.push_sync_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.push_sync_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val KEY_CHAT = "chat"
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 0x5EC
        private const val MIN_GAP_MS = 15_000L

        /** Коли прийшло останнє сповіщення для чату й коли почалася остання синхронізація (у межах процесу). */
        internal val lastPush = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val lastSync = java.util.concurrent.ConcurrentHashMap<String, Long>()

        fun enqueue(context: Context, chat: String) {
            val request = OneTimeWorkRequestBuilder<PushSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_CHAT to chat))
                .build()
            // У чергу за поточною: зайві запуски нічого не читають (див. lastPush/lastSync у doWork).
            WorkManager.getInstance(context).enqueueUniqueWork("push_$chat", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
