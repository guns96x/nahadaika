package ua.nahadaika.share

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import java.util.Date

/**
 * «Кур'єр» медіа через Firestore, поки немає Firebase Storage (план Spark без картки).
 * Опис файлу — chats/{chat}/media/{нагадування.ext}, сам файл — шматками до 900 КБ поруч: .../media/{нагадування.ext}~{n}.
 * Шматки — не підколекція, а сусіди з власним expireAt: так прибирання за терміном знаходить і їх,
 * навіть якщо опис уже зник (сміття живе щонайбільше 31 день — це перевіряють правила).
 * Файл живе в хмарі лише до доставки: щойно його забрали всі інші учасники, останній отримувач видаляє.
 * Що ніхто не забрав, зникає за 30 днів (на Spark TTL-політик немає — прибирають самі телефони).
 */
internal class MediaCourier(context: Context, private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    private val prefs = context.getSharedPreferences("courier", Context.MODE_PRIVATE)

    // Шматок майже 1 МБ на повільному мобільному інтернеті може йти довго.
    private suspend fun <T> Task<T>.confirmed(): T = withTimeout(120_000) { await() }

    private fun chat(chatId: String) = db.collection("chats").document(chatId)
    private fun media(chatId: String) = chat(chatId).collection("media")
    private fun part(chatId: String, docId: String, index: Int) = media(chatId).document("$docId~$index")

    /** chats/{chat}/media/{doc} → (chat, doc). */
    private fun parse(mediaRef: String): Pair<String, String> {
        check(MediaSync.isSafeMediaRef(mediaRef))
        val parts = mediaRef.split('/')
        return parts[1] to parts[3]
    }

    suspend fun upload(chatId: String, reminderId: String, file: File, mime: String): UploadedMedia {
        val size = file.length()
        check(size in 1..MAX_BYTES)
        val ref = MediaSync.storagePath(chatId, reminderId, file.extension.ifEmpty { "bin" })
        val meta = media(chatId).document(parse(ref).second)
        // Опис пишеться останнім: якщо він є, файл уже повністю на сервері (повтор після збою зв'язку).
        val existing = meta.get(Source.SERVER).confirmed()
        if (existing.exists()) {
            return UploadedMedia(ref, existing.getLong("size") ?: size, existing.getString("mime") ?: mime)
        }
        val expireAt = Timestamp(Date(System.currentTimeMillis() + KEEP_MS))
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        withContext(Dispatchers.IO) { file.inputStream().buffered() }.use { input ->
            val buffer = ByteArray(PART_BYTES)
            while (true) {
                val read = withContext(Dispatchers.IO) { input.fill(buffer) }
                if (read <= 0) break
                digest.update(buffer, 0, read)
                part(chatId, meta.id, count)
                    .set(mapOf("data" to Blob.fromBytes(buffer.copyOf(read)), "reminderId" to reminderId, "expireAt" to expireAt))
                    .confirmed()
                count++
            }
        }
        check(count in 1..MAX_PARTS)
        meta.set(
            mapOf(
                "reminderId" to reminderId,
                "uploader" to checkNotNull(auth.currentUser).uid,
                "size" to size,
                "mime" to mime,
                "sha256" to digest.digest().hex(),
                "parts" to count,
                "receivedBy" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to expireAt,
            ),
        ).confirmed()
        return UploadedMedia(ref, size, mime)
    }

    /** true — файл у [target] і сходиться з описом; false — не вдалося; null — у кур'єра такого немає. */
    suspend fun download(mediaRef: String, target: File): Boolean? {
        if (prefs.getBoolean(mediaRef, false)) return null
        val (chatId, docId) = parse(mediaRef)
        val meta = media(chatId).document(docId).get(Source.SERVER).confirmed()
        if (!meta.exists()) return null
        val parts = meta.getLong("parts")?.toInt() ?: return false
        val size = meta.getLong("size") ?: return false
        if (parts !in 1..MAX_PARTS || size !in 1..MAX_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        withContext(Dispatchers.IO) { target.outputStream() }.use { out ->
            for (i in 0 until parts) {
                val bytes = part(chatId, docId, i).get(Source.SERVER).confirmed()
                    .getBlob("data")?.toBytes() ?: return false
                digest.update(bytes)
                withContext(Dispatchers.IO) { out.write(bytes) }
            }
        }
        return target.length() == size && digest.digest().hex() == meta.getString("sha256")
    }

    /** Опису немає, а Storage теж немає — файл уже доставили всім або він прострочився: більше не питати сервер. */
    fun markGone(mediaRef: String) {
        prefs.edit().putBoolean(mediaRef, true).apply()
    }

    /** Позначити, що файл отримано; якщо тепер його мають усі інші учасники — прибрати з хмари. */
    suspend fun received(chatId: String, mediaRef: String) {
        val uid = auth.currentUser?.uid ?: return
        val meta = media(chatId).document(parse(mediaRef).second)
        val chatRef = chat(chatId)
        // Транзакція: двоє отримувачів одночасно не пропустять момент «отримали всі».
        val parts = db.runTransaction { tx ->
            // У транзакції Firestore спершу всі читання, потім записи.
            val m = tx.get(meta)
            if (!m.exists()) return@runTransaction null
            val members = (tx.get(chatRef).get("members") as? List<*>).orEmpty().filterIsInstance<String>()
            val uploader = m.getString("uploader")
            val got = (m.get("receivedBy") as? List<*>).orEmpty().filterIsInstance<String>().toMutableSet()
            if (uid != uploader && uid !in got) {
                tx.update(meta, "receivedBy", FieldValue.arrayUnion(uid))
                got += uid
            }
            val waiting = members.filter { it != uploader && it !in got }
            if (members.size > 1 && waiting.isEmpty()) m.getLong("parts")?.toInt() else null
        }.confirmed() ?: return
        delete(chatId, meta.id, parts)
    }

    private suspend fun delete(chatId: String, docId: String, parts: Int) {
        val batch = db.batch()
        for (i in 0 until parts.coerceIn(0, MAX_PARTS)) batch.delete(part(chatId, docId, i))
        batch.delete(media(chatId).document(docId))
        batch.commit().confirmed()
    }

    /** Видалити знайдені документи (описи й шматки) пачками. */
    private suspend fun deleteAll(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().confirmed()
        }
    }

    /** Нагадування видалили — його файл більше нікому не потрібен (опис і шматки мають reminderId). */
    suspend fun deleteFor(chatId: String, reminderId: String) {
        deleteAll(media(chatId).whereEqualTo("reminderId", reminderId).get(Source.SERVER).confirmed().documents)
    }

    /** Раз на добу прибрати прострочене (TTL-політики Firestore на Spark недоступні). */
    suspend fun cleanup(chatId: String) {
        val key = "cleanup_$chatId"
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0) < DAY_MS) return
        // І описи, і шматки (зокрема ті, чий опис зник), — у них у всіх є expireAt.
        deleteAll(media(chatId).whereLessThan("expireAt", Timestamp(Date(now))).get(Source.SERVER).confirmed().documents)
        prefs.edit().putLong(key, now).apply()
    }

    companion object {
        /** Документ Firestore — до 1 МіБ разом із назвами полів; 900 КБ лишає запас. */
        const val PART_BYTES = 900 * 1024
        const val MAX_BYTES = MediaSync.MAX_MEDIA_SIZE_BYTES
        const val MAX_PARTS = 32
        const val DAY_MS = 24 * 60 * 60_000L
        const val KEEP_MS = 30 * DAY_MS

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

        /** Заповнити буфер до кінця (readNBytes з'явився лише в Android 13). */
        private fun java.io.InputStream.fill(buffer: ByteArray): Int {
            var total = 0
            while (total < buffer.size) {
                val n = read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            return total
        }
    }
}
