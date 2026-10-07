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
import ua.nahadaika.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Підключення спільних чатів до Firebase. Налаштування проєкту — з local.properties
 * (firebase.apiKey, firebase.appId, firebase.projectId); без них функція просто прихована.
 */
object SharedSetup {
    fun init(context: Context) {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank() || Build.FINGERPRINT == "robolectric") return
        val app = FirebaseApp.getApps(context).firstOrNull() ?: runCatching {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }.getOrNull() ?: return
        SharedChats.init(context, FirestoreBackend(FirebaseAuth.getInstance(app), FirebaseFirestore.getInstance(app)))
        SharedSyncWorker.schedule(context)
    }
}

/** Коли застосунок закрито: раз на пів години забрати нове зі спільних чатів і поставити будильники. */
class SharedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { SharedChats.syncOnce() }.fold({ Result.success() }, { Result.retry() })

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SharedSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("shared_sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
