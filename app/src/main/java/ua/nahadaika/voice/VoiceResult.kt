package ua.nahadaika.voice

import org.json.JSONException
import org.json.JSONObject
import ua.nahadaika.data.Repeat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Одне нагадування, розпізнане з голосу. [at] = null — час не названо або він неправдоподібний. */
data class VoiceDraft(val what: String, val at: Long?, val repeat: Repeat, val alarm: Boolean)

data class VoiceResult(val transcript: String, val drafts: List<VoiceDraft>)

/**
 * Перевірка відповіді моделі. Довіряємо лише структурі: час мусить бути в майбутньому й не далі
 * кількох років, повтор — з дозволених значень, усе інше відкидається. Довільний текст моделі нікуди не виводимо.
 */
object VoiceResponseParser {
    const val MAX_REMINDERS = 5
    private const val MAX_YEARS = 5L
    private const val MAX_WHAT = 200
    private const val MAX_TRANSCRIPT = 500

    fun parse(json: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): VoiceResult? {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val list = root.optJSONArray("reminders")
        val drafts = buildList {
            for (i in 0 until minOf(list?.length() ?: 0, MAX_REMINDERS)) {
                val item = list?.optJSONObject(i) ?: continue
                val what = clean(item.optString("what"), MAX_WHAT)
                val at = parseTime(item.optString("when").takeIf { !item.isNull("when") }, now, zone)
                // Порожній текст без часу — сміття; порожній текст із часом — таймер чи будильник.
                if (what.isEmpty() && at == null) continue
                add(VoiceDraft(what, at, parseRepeat(item.optString("repeat")), item.optBoolean("alarm", false)))
            }
        }
        return VoiceResult(clean(root.optString("transcript"), MAX_TRANSCRIPT), drafts)
    }

    private fun parseTime(value: String?, now: Long, zone: ZoneId): Long? {
        val text = value?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val at = try {
            LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            return null
        }
        val limit = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).plusYears(MAX_YEARS)
            .atZone(zone).toInstant().toEpochMilli()
        return at.takeIf { it > now && it <= limit }
    }

    private fun parseRepeat(value: String): Repeat =
        Repeat.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: Repeat.NONE

    /** Без керівних символів і зайвих пробілів; довге обрізається. */
    private fun clean(text: String, max: Int): String =
        text.filter { !it.isISOControl() || it == '\n' }.replace(Regex("\\s+"), " ").trim().take(max)
}
