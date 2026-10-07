package ua.nahadaika.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import ua.nahadaika.BuildConfig
import ua.nahadaika.Prefs
import ua.nahadaika.data.Kind
import ua.nahadaika.media.Attachment
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId

/**
 * «Розумний час»: з голосового, кружечка чи сказаного вголос дізнатися, що й коли нагадати. Доступний лише там,
 * де налаштовано Firebase (варіант github); без нього застосунок просто просить обрати час, як і раніше.
 */
@SuppressLint("StaticFieldLeak") // лише applicationContext
object SmartVoice {
    /** Підміна в тестах. */
    var interpreter: VoiceInterpreter? = null

    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private val default: VoiceInterpreter? by lazy {
        val ctx = app
        val key = BuildConfig.FIREBASE_API_KEY
        val project = BuildConfig.FIREBASE_PROJECT_ID
        if (ctx == null || key.isBlank() || project.isBlank() || Build.FINGERPRINT == "robolectric") {
            null
        } else {
            GeminiRestInterpreter(project, key, BuildConfig.GEMINI_MODEL, ctx.packageName, certSha1(ctx))
        }
    }

    private fun active(): VoiceInterpreter? = interpreter ?: default

    fun available(): Boolean = active() != null

    suspend fun interpret(context: Context, attachment: Attachment, now: Long = System.currentTimeMillis()): VoiceOutcome {
        val engine = active() ?: return VoiceOutcome.Failed("unavailable")
        val temp = if (attachment.kind == Kind.VIDEO) File(context.cacheDir, "voice-${System.nanoTime()}.m4a") else null
        try {
            val audio = when (attachment.kind) {
                Kind.VOICE -> attachment.file
                Kind.VIDEO -> temp!!.takeIf { AudioExtractor.extract(attachment.file, it) } ?: return VoiceOutcome.Failed("no audio")
                else -> return VoiceOutcome.Failed("unsupported")
            }
            return engine.interpret(audio, "audio/mp4", context(context, now))
        } finally {
            temp?.delete()
        }
    }

    /** Текст, який уже розпізнав системний розпізнавач («Сказати»): у Gemini йде лише він, без звуку. */
    suspend fun interpretText(context: Context, text: String, now: Long = System.currentTimeMillis()): VoiceOutcome {
        val engine = active() ?: return VoiceOutcome.Failed("unavailable")
        return engine.interpretText(text, context(context, now))
    }

    private fun context(context: Context, now: Long) = VoiceContext(now, ZoneId.systemDefault(), Prefs.defaultHour(context))

    /** SHA-1 підпису застосунку — для X-Android-Cert (так робить і Firebase SDK). */
    @Suppress("DEPRECATION")
    private fun certSha1(context: Context): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                ?.apkContentsSigners
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        info?.firstOrNull()?.toByteArray()?.let { MessageDigest.getInstance("SHA-1").digest(it).joinToString("") { b -> "%02X".format(b) } }
    }.getOrNull()
}
