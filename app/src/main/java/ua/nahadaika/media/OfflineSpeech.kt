package ua.nahadaika.media

import android.content.Context
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.min

/**
 * Офлайн-розпізнавання української (Vosk). Модель завантажується один раз на вимогу
 * і далі працює без інтернету — для телефонів, чий системний розпізнавач не вміє читати записи.
 */
object OfflineSpeech {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip"
    const val DOWNLOAD_MB = 140
    const val DISK_MB = 420

    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Missing)
        private set

    private lateinit var appContext: Context
    private val filesDir get() = appContext.filesDir
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val lock = Mutex()
    private var model: Model? = null

    private val modelDir get() = File(filesDir, "vosk-uk")
    private val readyMark get() = File(modelDir, ".ready")

    fun init(context: Context) {
        appContext = context.applicationContext
        state = if (readyMark.exists()) State.Ready else State.Missing
    }

    val isReady get() = state == State.Ready

    fun download() {
        if (job?.isActive == true || isReady) return
        job = scope.launch {
            state = State.Downloading(0f)
            val part = File(filesDir, "vosk-uk.part")
            try {
                part.deleteRecursively()
                val storage = appContext.getSystemService(StorageManager::class.java)
                val free = runCatching { storage.getAllocatableBytes(storage.getUuidForPath(filesDir)) }.getOrDefault(Long.MAX_VALUE)
                if (free < (DISK_MB + 150L) * 1024 * 1024) {
                    throw IllegalStateException("недостатньо місця на телефоні (потрібно ≈$DISK_MB МБ)")
                }
                val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("сервер відповів ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: (DOWNLOAD_MB * 1024L * 1024)
                var shown = -1
                val counting = Counting(conn.inputStream) { read ->
                    val percent = (read * 100 / total).toInt().coerceIn(0, 99)
                    if (percent != shown) {
                        shown = percent
                        state = State.Downloading(percent / 100f)
                    }
                }
                ZipInputStream(counting.buffered()).use { zip ->
                    val root = part.canonicalPath + File.separator
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        ensureActive()
                        // Архів містить одну теку верхнього рівня — її відкидаємо.
                        val name = entry.name.substringAfter('/', "")
                        if (name.isEmpty()) continue
                        val out = File(part, name)
                        if (!out.canonicalPath.startsWith(root)) continue
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                File(part, ".ready").writeText("ok")
                modelDir.deleteRecursively()
                if (!part.renameTo(modelDir)) throw IllegalStateException("не вдалося зберегти модель")
                state = State.Ready
            } catch (e: CancellationException) {
                state = State.Missing
                throw e
            } catch (e: Exception) {
                state = State.Failed(e.message ?: "немає з'єднання з інтернетом")
            } finally {
                part.deleteRecursively()
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    /** Підвантажити модель заздалегідь (кілька секунд), щоб розпізнавання після запису було миттєвим. */
    suspend fun warmUp() {
        loadModel()
    }

    private suspend fun loadModel(): Model? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!isReady) return@withLock null
            model ?: runCatching { Model(modelDir.path) }.getOrNull().also { model = it }
        }
    }

    /** Розпізнати моно PCM 16 біт, 16 кГц. */
    suspend fun recognize(pcm: ByteArray): String? = withContext(Dispatchers.IO) {
        val m = loadModel() ?: return@withContext null
        runCatching {
            Recognizer(m, 16_000f).use { r ->
                var i = 0
                while (i < pcm.size) {
                    ensureActive()
                    val n = min(8_000, pcm.size - i)
                    r.acceptWaveForm(pcm.copyOfRange(i, i + n), n)
                    i += n
                }
                JSONObject(r.finalResult).optString("text").trim().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private class Counting(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) onRead(++count) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it > 0) {
                count += it
                onRead(count)
            }
        }
    }
}
