package ua.nahadaika.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.nahadaika.BuildConfig
import ua.nahadaika.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Файл оновлення в релізі на GitHub. */
data class UpdateAsset(val url: String, val size: Long, val sha256: String)

/** Нова версія: повний APK і, якщо є, патч від встановленої версії. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apk: UpdateAsset,
    val patch: UpdateAsset? = null,
    val patchFromSha256: String? = null,
) {
    /** Скільки доведеться завантажити, якщо патч підійде. */
    val downloadSize get() = patch?.size ?: apk.size

    companion object {
        /** Розбір update.json; [urls] — адреси файлів релізу за іменами. */
        fun parse(json: String, urls: Map<String, String>, currentCode: Long): UpdateInfo {
            val o = JSONObject(json)
            fun asset(a: JSONObject) = UpdateAsset(
                url = urls[a.getString("name")] ?: error("у релізі немає файлу ${a.getString("name")}"),
                size = a.getLong("size"),
                sha256 = a.getString("sha256").lowercase(),
            )
            val patches = o.optJSONArray("patches")
            val patch = (0 until (patches?.length() ?: 0)).map { patches!!.getJSONObject(it) }
                .firstOrNull { it.getLong("from") == currentCode && urls.containsKey(it.getString("name")) }
            return UpdateInfo(
                versionCode = o.getLong("versionCode"),
                versionName = o.getString("versionName"),
                notes = o.optString("notes"),
                apk = asset(o.getJSONObject("apk")),
                patch = patch?.let(::asset),
                patchFromSha256 = patch?.getString("fromSha256")?.lowercase(),
            )
        }
    }
}

/**
 * Оновлення з GitHub Releases: перевірка, завантаження (патч або повний APK) і встановлення.
 * Android завжди встановлює цілий APK, але завантажувати можна лише різницю між версіями.
 */
object Updates {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float, val patch: Boolean) : State
        data class ReadyToInstall(val info: UpdateInfo, val file: File) : State
        data class Failed(val message: String, val info: UpdateInfo? = null) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        internal set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** Перевірити, чи є нова версія. [silent] — не показувати помилки (фонова перевірка). */
    fun check(context: Context, silent: Boolean = false) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            if (!silent) state = State.Checking
            state = try {
                val info = fetch(app)
                Prefs.setLastUpdateCheck(app, System.currentTimeMillis())
                if (info != null) State.Available(info) else State.UpToDate
            } catch (e: Exception) {
                if (silent) state else State.Failed("Не вдалося перевірити: ${e.message ?: "немає інтернету"}")
            }
        }
    }

    /** Остання версія з GitHub або null, якщо встановлена вже найновіша. */
    suspend fun fetch(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null // релізів ще немає
        if (conn.responseCode != HttpURLConnection.HTTP_OK) error("GitHub відповів ${conn.responseCode}")
        val release = JSONObject(conn.inputStream.bufferedReader().readText())
        val assets = release.getJSONArray("assets")
        val urls = (0 until assets.length()).associate {
            val a = assets.getJSONObject(it)
            a.getString("name") to a.getString("browser_download_url")
        }
        val json = urls["update.json"] ?: return@withContext null
        val text = open(json).inputStream.bufferedReader().readText()
        val current = currentVersionCode(context)
        UpdateInfo.parse(text, urls, current).takeIf { it.versionCode > current }
    }

    /** Завантажити оновлення (патч, якщо підходить, інакше повний APK) і запустити встановлення. */
    fun download(context: Context, info: UpdateInfo) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { obtainApk(app, info) }
                state = State.ReadyToInstall(info, file)
                install(app, info, file)
            } catch (e: Exception) {
                state = State.Failed("Не вдалося завантажити: ${e.message ?: "немає інтернету"}", info)
            }
        }
    }

    private suspend fun obtainApk(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Nahadaika-${info.versionName}.apk")
        val patch = info.patch
        if (patch != null && info.patchFromSha256 != null) {
            val installed = File(context.applicationInfo.sourceDir)
            if (sha256(installed) == info.patchFromSha256) {
                runCatching {
                    state = State.Downloading(info, 0f, patch = true)
                    val bytes = get(patch, File(dir, "update.patch")) { state = State.Downloading(info, it, patch = true) }.readBytes()
                    val result = BsPatch.apply(installed.readBytes(), bytes)
                    check(sha256(result) == info.apk.sha256) { "контрольна сума не збіглася" }
                    target.writeBytes(result)
                    return target
                }
                // Патч не підійшов — беремо повний APK.
            }
        }
        state = State.Downloading(info, 0f, patch = false)
        get(info.apk, target) { state = State.Downloading(info, it, patch = false) }
        check(sha256(target) == info.apk.sha256) { "файл пошкоджено під час завантаження" }
        return target
    }

    private suspend fun get(asset: UpdateAsset, file: File, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val conn = open(asset.url)
        if (conn.responseCode != HttpURLConnection.HTTP_OK) error("сервер відповів ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
        var read = 0L
        var shown = -1
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    val p = (read * 100 / total).toInt().coerceIn(0, 100)
                    if (p != shown) {
                        shown = p
                        withContext(Dispatchers.Main) { progress(p / 100f) }
                    }
                }
            }
        }
        file
    }

    /** Встановити завантажений APK. Якщо бракує дозволу — відкрити системне налаштування. */
    fun install(context: Context, info: UpdateInfo, file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            state = State.ReadyToInstall(info, file)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        try {
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                val pi = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
                )
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            state = State.Failed("Не вдалося встановити: ${e.message}", info)
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Nahadaika-Android")
    }

    internal fun sha256(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
