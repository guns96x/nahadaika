package ua.nahadaika.media

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ua.nahadaika.Prefs
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.math.min

private const val LANGUAGE = "uk-UA"
private const val TARGET_RATE = 16_000

private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

private fun Bundle.firstResult(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

/** Що вдалося почути в записі. */
sealed interface Hearing {
    data class Heard(val text: String) : Hearing

    /** Телефон сам не вміє розпізнавати записи, а офлайн-розпізнавання ще не завантажене. */
    data object NeedsModel : Hearing

    /** Розпізнавач спрацював, але слів не почув. */
    data object Nothing : Hearing
}

/**
 * Розпізнавання вже записаного голосового чи відео — у фоні, без діалогу Google.
 * Звук із файлу декодується в PCM і йде в офлайн-розпізнавач (Vosk), а якщо його ще немає —
 * у системний розпізнавач (Android 13+; вміють далеко не всі телефони).
 */
object Transcriber {
    /** [good] — чи підходить розпізнане (наприклад, чи є в ньому час): тоді інші мовні пакети не пробуємо. */
    suspend fun transcribe(context: Context, file: File, good: (String) -> Boolean = { true }): Hearing {
        val pcm = withContext(Dispatchers.IO) { runCatching { decodeToMono16k(file) }.getOrNull() }
        if (pcm == null || pcm.isEmpty()) return Hearing.Nothing
        if (OfflineSpeech.isReady) {
            return OfflineSpeech.recognize(pcm, Prefs.speechPrimary(context), good)?.let(Hearing::Heard) ?: Hearing.Nothing
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Prefs.systemFileSpeechFailed(context)) {
            // Спершу розпізнавач на пристрої, потім стандартний.
            val kinds = listOfNotNull(
                true.takeIf { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) },
                false.takeIf { SpeechRecognizer.isRecognitionAvailable(context) },
            )
            for (local in kinds) {
                val text = withTimeoutOrNull(10_000) { recognize(context, pcm, local) }
                if (!text.isNullOrBlank()) return Hearing.Heard(text)
            }
            // Не вміє — більше не чекатимемо на нього щоразу.
            Prefs.setSystemFileSpeechFailed(context)
        }
        return Hearing.NeedsModel
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(context: Context, pcm: ByteArray, onDevice: Boolean): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val recognizer = runCatching {
                if (onDevice) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }.getOrNull()
            if (recognizer == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
            fun done(text: String?) {
                if (cont.isActive) cont.resume(text)
                recognizer.destroy()
                runCatching { readEnd.close() }
            }
            recognizer.setRecognitionListener(object : SimpleListener() {
                override fun onResults(results: Bundle) = done(results.firstResult())
                override fun onError(error: Int) = done(null)
            })
            recognizer.startListening(
                recognizerIntent()
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, TARGET_RATE),
            )
            thread(name = "speech-feed") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                        var i = 0
                        while (i < pcm.size) {
                            val n = min(3200, pcm.size - i)
                            out.write(pcm, i, n)
                            i += n
                        }
                    }
                } catch (_: IOException) {
                    // Розпізнавач закрив канал раніше — це нормально.
                }
            }
            cont.invokeOnCancellation {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    /** Декодує звукову доріжку (AAC у m4a/mp4) у моно PCM 16 біт, 16 кГц. */
    private fun decodeToMono16k(file: File): ByteArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val raw = ByteArrayOutputStream()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        val chunk = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.get(chunk, 0, info.size)
                        raw.write(chunk)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return toMono16k(raw.toByteArray(), rate, channels)
        } finally {
            extractor.release()
        }
    }

    /** Змішує канали в моно та переводить частоту в 16 кГц (лінійна інтерполяція). */
    private fun toMono16k(pcm: ByteArray, rate: Int, channels: Int): ByteArray {
        val src = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = src.remaining() / channels
        val mono = FloatArray(frames) { f ->
            var sum = 0f
            for (c in 0 until channels) sum += src.get(f * channels + c)
            sum / channels
        }
        val outFrames = (frames.toLong() * TARGET_RATE / rate).toInt()
        val out = ByteBuffer.allocate(outFrames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outFrames) {
            val pos = i.toDouble() * rate / TARGET_RATE
            val a = pos.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val t = (pos - a).toFloat()
            out.putShort((mono[a] * (1 - t) + mono[b] * t).toInt().coerceIn(-32768, 32767).toShort())
        }
        return out.array()
    }
}

/** Голосова команда прямо в полі вводу — без вікна Google: «Слухаю…» і текст наживо. */
class LiveDictation(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        cancel()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        partial = ""
        listening = true
        sr.setRecognitionListener(object : SimpleListener() {
            override fun onPartialResults(partialResults: Bundle) {
                partialResults.firstResult()?.let { partial = it }
            }

            override fun onResults(results: Bundle) {
                val text = results.firstResult() ?: partial.takeIf { it.isNotBlank() }
                finish()
                if (text != null) onResult(text) else onError(errorMessage(SpeechRecognizer.ERROR_NO_MATCH))
            }

            override fun onError(error: Int) {
                val text = partial.takeIf { it.isNotBlank() }
                finish()
                if (text != null) onResult(text) else onError(errorMessage(error))
            }
        })
        sr.startListening(recognizerIntent().putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }

    /** Досить слухати — розпізнати сказане. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        finish()
    }

    private fun finish() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }
}

/** Зрозуміле пояснення, чому розпізнавач не спрацював. */
private fun errorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не почув — спробуйте ще раз"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> "Немає зв'язку з розпізнаванням Google — перевірте інтернет"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Немає доступу до мікрофона"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Розпізнавач зайнятий — спробуйте ще раз"
    SpeechRecognizer.ERROR_AUDIO -> "Мікрофон зайнятий іншою програмою"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        "Розпізнавач Google не підтримує українську на цьому телефоні"
    else -> "Помилка розпізнавання (код $error)"
}

private open class SimpleListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle) = Unit
    override fun onPartialResults(partialResults: Bundle) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
