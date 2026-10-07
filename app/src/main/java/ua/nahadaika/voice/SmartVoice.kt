package ua.nahadaika.voice

import android.content.Context
import ua.nahadaika.BuildConfig
import ua.nahadaika.Prefs
import ua.nahadaika.data.Kind
import ua.nahadaika.media.Attachment
import java.io.File
import java.time.ZoneId

/**
 * «Розумний час»: з голосового чи кружечка дізнатися, що й коли нагадати. Доступний лише там, де є ключ
 * (прототип) і дозвіл на інтернет; без нього застосунок просто просить обрати час, як і раніше.
 */
object SmartVoice {
    /** Підміна в тестах. */
    var interpreter: VoiceInterpreter? = null

    private val default: VoiceInterpreter? by lazy {
        BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }?.let { GeminiRestInterpreter(it, BuildConfig.GEMINI_MODEL) }
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
            val ctx = VoiceContext(now, ZoneId.systemDefault(), Prefs.defaultHour(context))
            return engine.interpret(audio, "audio/mp4", ctx)
        } finally {
            temp?.delete()
        }
    }
}
