package ua.nahadaika.media

import android.content.Context
import android.os.storage.StorageManager
import androidx.compose.runtime.mutableStateMapOf
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

/** Мовний пакет для офлайн-розпізнавання. */
enum class SpeechPack(val title: String, val url: String, val downloadMb: Int, val diskMb: Int, val dirName: String) {
    UK("Українська", "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip", 140, 420, "vosk-uk"),
    RU("Русский", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45, 90, "vosk-ru"),
}

/**
 * Офлайн-розпізнавання мовлення (Vosk). Мовні пакети завантажуються на вимогу
 * і далі працюють без інтернету — для телефонів, чий системний розпізнавач не вміє читати записи.
 */
object OfflineSpeech {
    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val states = mutableStateMapOf<SpeechPack, State>()

    fun state(pack: SpeechPack): State = states[pack] ?: State.Missing

    /** Основний пакет — українська (для пропозицій і банерів). */
    val state: State get() = state(SpeechPack.UK)

    private lateinit var appContext: Context
    private val filesDir get() = appContext.filesDir
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<SpeechPack, Job>()
    private val lock = Mutex()
    private val models = mutableMapOf<SpeechPack, Model>()

    private fun dir(pack: SpeechPack) = File(filesDir, pack.dirName)

    fun init(context: Context) {
        appContext = context.applicationContext
        SpeechPack.entries.forEach { states[it] = if (File(dir(it), ".ready").exists()) State.Ready else State.Missing }
    }

    fun isReady(pack: SpeechPack) = state(pack) == State.Ready

    /** Є хоч один встановлений пакет. */
    val isReady get() = SpeechPack.entries.any(::isReady)

    val readyPacks get() = SpeechPack.entries.filter(::isReady)

    /** Скільки місця займає встановлений пакет, МБ. */
    fun sizeMb(pack: SpeechPack): Long =
        dir(pack).walkBottomUp().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)

    fun download(pack: SpeechPack = SpeechPack.UK) {
        if (jobs[pack]?.isActive == true || isReady(pack)) return
        jobs[pack] = scope.launch {
            states[pack] = State.Downloading(0f)
            val part = File(filesDir, "${pack.dirName}.part")
            try {
                part.deleteRecursively()
                val storage = appContext.getSystemService(StorageManager::class.java)
                val free = runCatching { storage.getAllocatableBytes(storage.getUuidForPath(filesDir)) }.getOrDefault(Long.MAX_VALUE)
                if (free < (pack.diskMb + 150L) * 1024 * 1024) {
                    throw IllegalStateException("недостатньо місця на телефоні (потрібно ≈${pack.diskMb} МБ)")
                }
                val conn = URL(pack.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("сервер відповів ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: (pack.downloadMb * 1024L * 1024)
                var shown = -1
                val counting = Counting(conn.inputStream) { read ->
                    val percent = (read * 100 / total).toInt().coerceIn(0, 99)
                    if (percent != shown) {
                        shown = percent
                        states[pack] = State.Downloading(percent / 100f)
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
                dir(pack).deleteRecursively()
                if (!part.renameTo(dir(pack))) throw IllegalStateException("не вдалося зберегти модель")
                states[pack] = State.Ready
            } catch (e: CancellationException) {
                states[pack] = State.Missing
                throw e
            } catch (e: Exception) {
                states[pack] = State.Failed(e.message ?: "немає з'єднання з інтернетом")
            } finally {
                part.deleteRecursively()
            }
        }
    }

    fun cancelDownload(pack: SpeechPack = SpeechPack.UK) {
        jobs[pack]?.cancel()
    }

    /** Видалити пакет, щоб звільнити місце. */
    fun delete(pack: SpeechPack) {
        cancelDownload(pack)
        scope.launch {
            lock.withLock { models.remove(pack)?.close() }
            dir(pack).deleteRecursively()
            states[pack] = State.Missing
        }
    }

    /** Підвантажити моделі заздалегідь (кілька секунд), щоб розпізнавання після запису було миттєвим. */
    suspend fun warmUp() {
        readyPacks.forEach { loadModel(it) }
    }

    private suspend fun loadModel(pack: SpeechPack): Model? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!isReady(pack)) return@withLock null
            models[pack] ?: runCatching { Model(dir(pack).path) }.getOrNull()?.also { models[pack] = it }
        }
    }

    /**
     * Розпізнати моно PCM 16 біт, 16 кГц усіма встановленими пакетами, починаючи з [first].
     * Повертає перший результат, який підходить під [good] (наприклад, містить час), інакше — перший непорожній.
     */
    suspend fun recognize(pcm: ByteArray, first: SpeechPack, good: (String) -> Boolean): String? {
        var fallback: String? = null
        for (pack in readyPacks.sortedBy { if (it == first) 0 else 1 }) {
            val text = recognizeWith(pack, pcm) ?: continue
            if (good(text)) return text
            if (fallback == null) fallback = text
        }
        return fallback
    }

    private suspend fun recognizeWith(pack: SpeechPack, pcm: ByteArray): String? = withContext(Dispatchers.IO) {
        val m = loadModel(pack) ?: return@withContext null
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
