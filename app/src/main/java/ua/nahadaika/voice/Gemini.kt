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

fun interface VoiceInterpreter {
    suspend fun interpret(audio: File, mime: String, context: VoiceContext): VoiceOutcome
}

/**
 * Прототип: прямий виклик Gemini Developer API з ключем (лише debug-збірка, ключ — з local.properties).
 * У продакшні його замінить Firebase AI Logic із шаблоном промпта на сервері (див. docs/ROADMAP.md, етап 3).
 */
class GeminiRestInterpreter(private val apiKey: String, private val model: String) : VoiceInterpreter {
    override suspend fun interpret(audio: File, mime: String, context: VoiceContext): VoiceOutcome = withContext(Dispatchers.IO) {
        if (audio.length() > MAX_AUDIO_BYTES) return@withContext VoiceOutcome.Failed("audio too large")
        try {
            val body = requestBody(audio, mime, context)
            val conn = (URL("$ENDPOINT/$model:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 40_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
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
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MAX_AUDIO_BYTES = 8_000_000L

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
                "when = local date-time yyyy-MM-ddTHH:mm in the user's time zone, or null if no time can be determined. Never answer with a time in the past.",
                "repeat = none, daily, weekly, monthly or yearly. alarm = true only if the user asked for an alarm, a wake-up or a timer.",
                "If the note contains no request to remind, return an empty reminders array.",
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

        internal fun requestBody(audio: File, mime: String, context: VoiceContext): String {
            val data = Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)
            val parts = JSONArray()
                .put(JSONObject().put("text", prompt(context)))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", data)))
            return JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema()).put("temperature", 0),
                )
                .toString()
        }
    }
}
