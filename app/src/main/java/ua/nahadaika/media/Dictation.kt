package ua.nahadaika.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ua.nahadaika.R
import ua.nahadaika.Res
import java.util.Locale

/**
 * «Сказати» — як мікрофон на клавіатурі: системний розпізнавач Google слухає живий мікрофон, текст з'являється наживо.
 * Це лише розпізнавання сказаного зараз; готові записи (голосові, кружечки) розпізнає Gemini.
 */
class LiveDictation(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    /** [onResult] — підсумковий текст; [onError] — зрозуміле пояснення, чому не вийшло. */
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

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Мова застосунку; українська лишається, якщо система не українська й не англійська.
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag().takeIf { Locale.getDefault().language in setOf("uk", "en") } ?: "uk-UA")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}

private fun Bundle.firstResult(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

/** Зрозуміле пояснення, чому розпізнавач не спрацював. */
private fun errorMessage(error: Int): String = Res.s(
    when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.dictation_err_nothing
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        -> R.string.dictation_err_network
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.dictation_err_permission
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.dictation_err_busy
        SpeechRecognizer.ERROR_AUDIO -> R.string.dictation_err_audio
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.dictation_err_language
        else -> R.string.dictation_err_other
    },
)

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
