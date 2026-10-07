package ua.nahadaika.voice

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.nahadaika.data.Repeat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Що відомо про «зараз» — модель рахує «завтра», «через 20 хвилин» від цього. */
data class VoiceContext(val now: Long, val zone: ZoneId, val defaultHour: Int)

sealed interface VoiceOutcome {
    data class Success(val result: VoiceResult) : VoiceOutcome
    data object Offline : VoiceOutcome
    data class Failed(val message: String) : VoiceOutcome
}

/** Розбирає сказане на нагадування: з готового звуку або з тексту, який уже розпізнав системний розпізнавач. */
interface VoiceInterpreter {
    suspend fun interpret(audio: File, mime: String, context: VoiceContext): VoiceOutcome

    suspend fun interpretText(text: String, context: VoiceContext): VoiceOutcome = VoiceOutcome.Failed("text is not supported")
}

/**
 * Gemini через Firebase AI Logic: запит іде на проксі Firebase з ключем застосунку, окремого ключа Gemini в застосунку немає.
 * [certSha1] і [packageName] додаються до запиту, щоб ключ можна було обмежити цим застосунком у консолі Google Cloud.
 * Далі на цьому ж проксі вмикається App Check (Play Integrity) — тоді скористатись ключем зможе лише справжня Нагадайка.
 */
class GeminiRestInterpreter(
    private val projectId: String,
    private val apiKey: String,
    private val model: String,
    private val packageName: String,
    private val certSha1: String?,
) : VoiceInterpreter {
    override suspend fun interpret(audio: File, mime: String, context: VoiceContext): VoiceOutcome {
        if (audio.length() > MAX_AUDIO_BYTES) return VoiceOutcome.Failed("audio too large")
        // Файл міг зникнути (видалили запис) — це не падіння, а просто «не вдалося».
        val data = withContext(Dispatchers.IO) { runCatching { Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP) }.getOrNull() }
            ?: return VoiceOutcome.Failed("audio unreadable")
        return send(requestBody(listOf(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", data))), context), context)
    }

    override suspend fun interpretText(text: String, context: VoiceContext): VoiceOutcome =
        send(requestBody(listOf(JSONObject().put("text", "Voice note, transcribed by a speech recognizer (may contain mistakes): $text")), context), context)

    private suspend fun send(body: String, context: VoiceContext): VoiceOutcome = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$ENDPOINT/projects/$projectId/models/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 40_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("X-Android-Package", packageName)
                certSha1?.let { setRequestProperty("X-Android-Cert", it) }
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext VoiceOutcome.Failed("HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val answer = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            VoiceResponseParser.parse(answer, context.now, context.zone)
                ?.let { VoiceOutcome.Success(it) } ?: VoiceOutcome.Failed("bad response")
        } catch (_: UnknownHostException) {
            VoiceOutcome.Offline
        } catch (e: IOException) {
            VoiceOutcome.Failed(e.message ?: "io")
        } catch (e: Exception) {
            VoiceOutcome.Failed(e.message ?: "error")
        }
    }

    companion object {
        private const val ENDPOINT = "https://firebasevertexai.googleapis.com/v1beta"
        private const val MAX_AUDIO_BYTES = 8_000_000L

        // Той самий промпт і схема — у tools/eval_gemini.py; міняючи тут, міняйте й там і перевіряйте якість.
        internal fun prompt(context: VoiceContext): String {
            val local = Instant.ofEpochMilli(context.now).atZone(context.zone)
            val now = local.toLocalDateTime().withSecond(0).withNano(0)
            val weekday = local.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val hour = "%02d".format(context.defaultHour)
            return listOf(
                "You turn a short voice note into reminders for a reminder app.",
                "The user speaks mostly Ukrainian, sometimes Russian or English.",
                "Current local time: $now ($weekday), time zone ${context.zone.id}.",
                "If only a day is named, use $hour:00. Weeks start on Monday.",
                "transcript: what was said, verbatim.",
                "reminders: one item per thing to remember. what = short text of the thing, without the time words, in the language of the speech.",
                "Always return an item for a thing to remember, even when no time is named (then when = null).",
                "when = local date-time yyyy-MM-ddTHH:mm in the user's time zone, or null if no time can be determined. Never answer with a time in the past.",
                "A day or date named once applies to the next items of the same note until another is named (tomorrow at 9 ... and at 7 pm: both tomorrow).",
                "repeat = none, daily, weekly, monthly or yearly; for a repeating item, when = its first upcoming occurrence.",
                "alarm = true only if the user asked for an alarm, a wake-up or a timer.",
                "If the note contains no request to remind or to remember anything, return an empty reminders array.",
            ).joinToString("\n")
        }

        internal fun schema(): JSONObject {
            fun type(name: String) = JSONObject().put("type", name)
            val item = JSONObject().put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject()
                        .put("what", type("STRING"))
                        .put("when", type("STRING").put("nullable", true))
                        .put("repeat", type("STRING").put("enum", JSONArray(Repeat.entries.map { it.name.lowercase() })))
                        .put("alarm", type("BOOLEAN")),
                )
                .put("required", JSONArray(listOf("what", "when", "repeat", "alarm")))
            return JSONObject().put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject()
                        .put("transcript", type("STRING"))
                        .put("reminders", type("ARRAY").put("items", item)),
                )
                .put("required", JSONArray(listOf("transcript", "reminders")))
        }

        /** Промпт + вміст запису (звук або текст) і вимога відповісти JSON за схемою. */
        internal fun requestBody(content: List<JSONObject>, context: VoiceContext): String {
            val parts = JSONArray().put(JSONObject().put("text", prompt(context)))
            content.forEach { parts.put(it) }
            return JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema()).put("temperature", 0),
                )
                .toString()
        }

        /** Для тестів: тіло запиту зі звуком із файлу. */
        internal fun requestBody(audio: File, mime: String, context: VoiceContext): String {
            val data = Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)
            return requestBody(listOf(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", data))), context)
        }
    }
}
