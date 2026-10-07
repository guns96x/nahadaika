package ua.nahadaika.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ua.nahadaika.R
import ua.nahadaika.Res
import ua.nahadaika.media.MediaFiles
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Підсумок відновлення: скільки додано, скільки вже було на телефоні. */
data class ImportResult(val chats: Int, val reminders: Int, val skipped: Int)

/** Резервна копія не схожа на файл Нагадайки або створена новішою версією. */
class BackupFormatException(message: String) : IOException(message)

/**
 * Резервна копія — ZIP: `data.json` (чати й нагадування) і `media/<файл>` (голосові, відео, фото).
 * Відновлення додає до наявного, а не замінює: чат з тією самою назвою поповнюється,
 * нагадування, що вже є (той самий чат, час, вид і текст), пропускаються.
 */
object Backup {
    private const val FORMAT = 1
    private const val DATA = "data.json"
    private const val MEDIA = "media/"
    private val safeName = Regex("[A-Za-z0-9._-]{1,100}")

    suspend fun export(db: AppDatabase, out: OutputStream) {
        val chats = db.chats().all()
        val reminders = db.reminders().all()
        val comments = reminders.associate { it.id to db.comments().byReminder(it.id) }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(toJson(chats, reminders, comments).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            reminders.mapNotNull { it.mediaPath }.distinct().map(::File).filter { it.isFile }.forEach { file ->
                zip.putNextEntry(ZipEntry(MEDIA + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun import(context: Context, db: AppDatabase, input: InputStream): ImportResult {
        val staging = File(context.cacheDir, "import-${System.nanoTime()}").apply { mkdirs() }
        try {
            val json = unpack(input, staging)
            val data = try {
                JSONObject(json)
            } catch (_: JSONException) {
                throw BackupFormatException(Res.s(R.string.core_backup_error_not_nahadaika))
            }
            if (data.optString("app") != "nahadaika") throw BackupFormatException(Res.s(R.string.core_backup_error_not_nahadaika))
            if (data.optInt("format") > FORMAT) throw BackupFormatException(Res.s(R.string.core_backup_error_newer_version))
            return merge(context, db, data, staging)
        } catch (e: JSONException) {
            throw BackupFormatException(Res.s(R.string.core_backup_error_corrupted))
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Розпаковує медіа в [staging] і повертає вміст `data.json`. Назви файлів перевіряються, щоб архів не писав поза [staging]. */
    private fun unpack(input: InputStream, staging: File): String {
        var json: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.isDirectory -> Unit
                    entry.name == DATA -> json = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(MEDIA) && safeName.matches(entry.name.removePrefix(MEDIA)) ->
                        File(staging, entry.name.removePrefix(MEDIA)).outputStream().use { zip.copyTo(it) }
                }
            }
        }
        return json ?: throw BackupFormatException(Res.s(R.string.core_backup_error_not_nahadaika))
    }

    private suspend fun merge(context: Context, db: AppDatabase, data: JSONObject, staging: File): ImportResult {
        val existingChats = db.chats().all().toMutableList()
        val chatIdMap = HashMap<Long, Long>()
        var addedChats = 0
        val chatsJson = data.getJSONArray("chats")
        for (i in 0 until chatsJson.length()) {
            val c = chatsJson.getJSONObject(i)
            val name = c.getString("name")
            val local = existingChats.firstOrNull { it.name == name } ?: run {
                val chat = Chat(name = name, color = c.getInt("color"), createdAt = c.optLong("createdAt", System.currentTimeMillis()))
                addedChats++
                chat.copy(id = db.chats().insert(chat)).also { existingChats += it }
            }
            chatIdMap[c.getLong("id")] = local.id
        }

        var added = 0
        var skipped = 0
        val now = System.currentTimeMillis()
        val known = db.reminders().all().map(::fingerprint).toMutableSet()
        val remindersJson = data.getJSONArray("reminders")
        for (i in 0 until remindersJson.length()) {
            val j = remindersJson.getJSONObject(i)
            val chatId = chatIdMap[j.getLong("chatId")] ?: continue
            val draft = fromJson(j, chatId, mediaPath = null)
            if (!known.add(fingerprint(draft))) {
                skipped++
                continue
            }
            val media = j.optString("media").takeIf { it.isNotEmpty() }?.let { File(staging, it) }?.takeIf { it.isFile }
            val target = media?.let { MediaFiles.newFile(context, it.extension.ifEmpty { "bin" }).also { t -> it.copyTo(t) } }
            val id = db.reminders().insert(settle(draft.copy(mediaPath = target?.absolutePath), now))
            val comments = j.optJSONArray("comments")
            for (k in 0 until (comments?.length() ?: 0)) {
                val c = comments!!.getJSONObject(k)
                db.comments().insert(
                    Comment(
                        reminderId = id,
                        text = c.getString("text"),
                        authorName = if (c.isNull("author")) null else c.getString("author"),
                        createdAt = c.optLong("createdAt", now),
                    ),
                )
            }
            added++
        }
        return ImportResult(chats = addedChats, reminders = added, skipped = skipped)
    }

    /**
     * Прострочене нагадування з копії не має засипати сповіщеннями: разове стає виконаним, повторюване — їде на наступний раз.
     */
    private fun settle(r: Reminder, now: Long): Reminder = when {
        r.fired || r.alarmAt() > now -> r
        r.repeat == Repeat.NONE -> r.copy(fired = true, snoozedUntil = null, lastFiredAt = r.lastFiredAt ?: r.alarmAt())
        else -> r.copy(triggerAt = Repo.nextOccurrence(r.triggerAt, r.repeat, now), snoozedUntil = null)
    }

    private fun fingerprint(r: Reminder) = listOf(r.chatId, r.kind, r.text, r.triggerAt, r.createdAt).joinToString("|")

    internal fun toJson(chats: List<Chat>, reminders: List<Reminder>, comments: Map<Long, List<Comment>> = emptyMap()): JSONObject = JSONObject()
        .put("app", "nahadaika")
        .put("format", FORMAT)
        .put("exportedAt", System.currentTimeMillis())
        .put("chats", JSONArray(chats.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("color", c.color).put("createdAt", c.createdAt)
        }))
        .put("reminders", JSONArray(reminders.map { r ->
            JSONObject()
                .put("chatId", r.chatId)
                .put("kind", r.kind.name)
                .put("text", r.text)
                .put("media", r.mediaPath?.let { File(it).name } ?: "")
                .put("durationMs", r.durationMs)
                .put("triggerAt", r.triggerAt)
                .put("repeat", r.repeat.name)
                .put("snoozedUntil", r.snoozedUntil ?: JSONObject.NULL)
                .put("fired", r.fired)
                .put("lastFiredAt", r.lastFiredAt ?: JSONObject.NULL)
                .put("createdAt", r.createdAt)
                .put("alarm", r.alarm)
                .put("comments", JSONArray(comments[r.id].orEmpty().map { c ->
                    JSONObject().put("text", c.text).put("author", c.authorName ?: JSONObject.NULL).put("createdAt", c.createdAt)
                }))
        }))

    private fun fromJson(j: JSONObject, chatId: Long, mediaPath: String?) = Reminder(
        chatId = chatId,
        kind = runCatching { Kind.valueOf(j.getString("kind")) }.getOrDefault(Kind.TEXT),
        text = j.optString("text"),
        mediaPath = mediaPath,
        durationMs = j.optLong("durationMs"),
        triggerAt = j.getLong("triggerAt"),
        repeat = runCatching { Repeat.valueOf(j.getString("repeat")) }.getOrDefault(Repeat.NONE),
        snoozedUntil = if (j.isNull("snoozedUntil")) null else j.getLong("snoozedUntil"),
        fired = j.optBoolean("fired"),
        lastFiredAt = if (j.isNull("lastFiredAt")) null else j.getLong("lastFiredAt"),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        alarm = j.optBoolean("alarm"),
    )
}
