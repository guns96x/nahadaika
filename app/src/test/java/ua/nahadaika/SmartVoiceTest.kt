package ua.nahadaika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Repeat
import ua.nahadaika.media.Attachment
import ua.nahadaika.voice.GeminiRestInterpreter
import ua.nahadaika.voice.SmartVoice
import ua.nahadaika.voice.VoiceContext
import ua.nahadaika.voice.VoiceInterpreter
import ua.nahadaika.voice.VoiceOutcome
import ua.nahadaika.voice.VoiceResponseParser
import ua.nahadaika.voice.VoiceResult
import java.time.LocalDateTime
import java.time.ZoneId

/** «Розумний час»: перевірка відповіді моделі, запит до Gemini і підключення до застосунку. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartVoiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Kyiv")

    // Фіксоване «зараз»: середа 30.09.2026 10:00 за Києвом.
    private val now = LocalDateTime.of(2026, 9, 30, 10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun parse(json: String) = VoiceResponseParser.parse(json, now, zone)

    @After fun reset() {
        SmartVoice.interpreter = null
    }

    @Test fun validAnswerBecomesDraft() {
        val r = parse(
            """{"transcript":"завтра о дев'ятій забрати посилку","reminders":[
                {"what":"Забрати посилку","when":"2026-10-01T09:00","repeat":"none","alarm":false}]}""",
        )!!
        assertEquals("завтра о дев'ятій забрати посилку", r.transcript)
        assertEquals(1, r.drafts.size)
        with(r.drafts[0]) {
            assertEquals("Забрати посилку", what)
            assertEquals(at("2026-10-01T09:00"), at)
            assertEquals(Repeat.NONE, repeat)
            assertFalse(alarm)
        }
    }

    @Test fun severalRemindersFromOnePhrase() {
        val r = parse(
            """{"transcript":"x","reminders":[
                {"what":"Хліб","when":"2026-10-01T09:00","repeat":"none","alarm":false},
                {"what":"Ліки","when":"2026-10-01T20:00","repeat":"daily","alarm":false}]}""",
        )!!
        assertEquals(listOf("Хліб", "Ліки"), r.drafts.map { it.what })
        assertEquals(Repeat.DAILY, r.drafts[1].repeat)
    }

    @Test fun missingTimeKeepsTextForManualPick() {
        val r = parse("""{"transcript":"x","reminders":[{"what":"Купити хліб","when":null,"repeat":"none","alarm":false}]}""")!!
        assertNull(r.drafts.single().at)
        assertEquals("Купити хліб", r.drafts.single().what)
    }

    @Test fun pastAndFarFutureTimesAreRejected() {
        val r = parse(
            """{"transcript":"x","reminders":[
                {"what":"Минуле","when":"2026-09-29T09:00","repeat":"none","alarm":false},
                {"what":"Далеке","when":"2040-01-01T09:00","repeat":"none","alarm":false},
                {"what":"Сміття","when":"завтра","repeat":"none","alarm":false}]}""",
        )!!
        assertTrue(r.drafts.all { it.at == null })
    }

    @Test fun unknownRepeatFallsBackToNone() {
        val r = parse("""{"transcript":"x","reminders":[{"what":"a","when":"2026-10-01T09:00","repeat":"hourly","alarm":true}]}""")!!
        assertEquals(Repeat.NONE, r.drafts.single().repeat)
        assertTrue(r.drafts.single().alarm)
    }

    @Test fun alarmWithoutTextIsKept_butEmptyJunkIsDropped() {
        val r = parse(
            """{"transcript":"розбуди о 6","reminders":[
                {"what":"","when":"2026-10-01T06:00","repeat":"none","alarm":true},
                {"what":"  ","when":null,"repeat":"none","alarm":false}]}""",
        )!!
        assertEquals(1, r.drafts.size)
        assertEquals("", r.drafts.single().what)
    }

    @Test fun listIsCappedAndTextCleaned() {
        val items = (1..9).joinToString(",") { """{"what":"n$it","when":"2026-10-01T09:00","repeat":"none","alarm":false}""" }
        assertEquals(VoiceResponseParser.MAX_REMINDERS, parse("""{"transcript":"x","reminders":[$items]}""")!!.drafts.size)
        val long = "а".repeat(1000)
        val what = parse("""{"transcript":"x","reminders":[{"what":"  $long\u0007\n\n b ","when":null,"repeat":"none","alarm":false}]}""")!!.drafts.single().what
        assertEquals(200, what.length)
        assertFalse(what.contains('\u0007'))
    }

    @Test fun garbageIsNull() {
        assertNull(parse("не json"))
        assertNull(parse(""))
        assertEquals(0, parse("""{"transcript":"привіт"}""")!!.drafts.size)
    }

    @Test fun requestCarriesContextSchemaAndAudio() {
        val file = java.io.File.createTempFile("voice", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)); deleteOnExit() }
        val body = JSONObject(GeminiRestInterpreter.requestBody(file, "audio/mp4", VoiceContext(now, zone, 9)))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val prompt = parts.getJSONObject(0).getString("text")
        assertTrue(prompt.contains("2026-09-30T10:00"))
        assertTrue(prompt.contains("Wednesday"))
        assertTrue(prompt.contains("Europe/Kyiv"))
        assertTrue(prompt.contains("09:00"))
        assertTrue("без часу — усе одно нагадування", prompt.contains("even when no time is named"))
        val inline = parts.getJSONObject(1).getJSONObject("inline_data")
        assertEquals("audio/mp4", inline.getString("mime_type"))
        assertEquals("AQID", inline.getString("data"))
        val cfg = body.getJSONObject("generationConfig")
        assertEquals("application/json", cfg.getString("responseMimeType"))
        val repeatEnum = cfg.getJSONObject("responseSchema").getJSONObject("properties").getJSONObject("reminders")
            .getJSONObject("items").getJSONObject("properties").getJSONObject("repeat").getJSONArray("enum")
        assertEquals(Repeat.entries.size, repeatEnum.length())
    }

    @Test fun interpretUsesInjectedEngineAndReportsOutcome() = runBlocking {
        assertFalse(SmartVoice.available())
        val file = java.io.File.createTempFile("voice", ".m4a").apply { deleteOnExit() }
        val result = VoiceResult("т", emptyList())
        SmartVoice.interpreter = object : VoiceInterpreter {
            override suspend fun interpret(audio: java.io.File, mime: String, context: VoiceContext): VoiceOutcome {
                assertEquals("audio/mp4", mime)
                assertEquals(now, context.now)
                return VoiceOutcome.Success(result)
            }

            override suspend fun interpretText(text: String, context: VoiceContext): VoiceOutcome {
                assertEquals("завтра о дев'ятій хліб", text)
                return VoiceOutcome.Success(VoiceResult(text, emptyList()))
            }
        }
        assertTrue(SmartVoice.available())
        val outcome = SmartVoice.interpret(app, Attachment(Kind.VOICE, file), now)
        assertEquals(result, (outcome as VoiceOutcome.Success).result)
        assertNotNull(Prefs.defaultHour(app))
        // Сказане вголос іде як текст, без звуку.
        assertEquals("завтра о дев'ятій хліб", (SmartVoice.interpretText(app, "завтра о дев'ятій хліб", now) as VoiceOutcome.Success).result.transcript)
    }

    @Test fun consentIsUnsetUntilChosen() {
        assertNull(Prefs.smartVoice(app))
        Prefs.setSmartVoice(app, false)
        assertEquals(false, Prefs.smartVoice(app))
        Prefs.setSmartVoice(app, true)
        assertEquals(true, Prefs.smartVoice(app))
    }
}
