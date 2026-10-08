package ua.nahadaika.share

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import ua.nahadaika.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Підключення спільних чатів до Firebase. Налаштування проєкту — з local.properties
 * (firebase.apiKey, firebase.appId, firebase.projectId); без них функція просто прихована.
 * Хмарне сховище Firebase Storage підключається за наявності firebase.storageBucket.
 */
object SharedSetup {
    fun init(context: Context) {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() || Build.FINGERPRINT == "robolectric") return
        val app = FirebaseApp.getApps(context).firstOrNull() ?: runCatching {
            val builder = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                // Для FCM: номер проєкту — друге поле App ID (1:НОМЕР:android:…).
                .setGcmSenderId(BuildConfig.FIREBASE_APP_ID.split(':').getOrElse(1) { "" })
            if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) {
                val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET.trim()
                builder.setStorageBucket(if (bucket.startsWith("gs://")) bucket.removePrefix("gs://") else bucket)
            }
            FirebaseApp.initializeApp(context, builder.build())
        }.getOrNull() ?: return

        val storage = if (BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) {
            runCatching {
                val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET.trim()
                if (bucket.startsWith("gs://")) FirebaseStorage.getInstance(app, bucket)
                else FirebaseStorage.getInstance(app, "gs://$bucket")
            }.getOrNull()
        } else null

        SharedChats.init(context, FirestoreBackend(context.applicationContext, FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app), storage))
        SharedSyncWorker.schedule(context)
    }
}

/**
 * Запасний шлях, коли застосунок закрито: раз на 2 години забрати нове зі спільних чатів і поставити будильники.
 * Основний — миттєвий push ([PushService]); цей рятує, якщо сервер сповіщень заснув чи FCM не дійшло.
 * Частіше не треба: кожен запуск читає всі документи всіх чатів, а це ліміт Spark (50 тис. читань на добу).
 */
class SharedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { SharedChats.syncOnce() }.fold({ Result.success() }, { Result.retry() })

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SharedSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("shared_sync", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
