package ua.nahadaika.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY createdAt")
    fun observeAll(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: Long): Flow<Chat?>

    @Query("SELECT * FROM chats ORDER BY createdAt")
    suspend fun all(): List<Chat>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: Long): Chat?

    @Query("SELECT COUNT(*) FROM chats")
    suspend fun count(): Int

    @Insert
    suspend fun insert(chat: Chat): Long

    @Update
    suspend fun update(chat: Chat)

    @Delete
    suspend fun delete(chat: Chat)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE chatId = :chatId")
    fun observeByChat(chatId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE chatId = :chatId")
    suspend fun byChat(chatId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE fired = 0")
    suspend fun pending(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE fired = 1")
    suspend fun done(): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE reminderId = :reminderId ORDER BY createdAt, id")
    fun observeByReminder(reminderId: Long): Flow<List<Comment>>

    @Query("SELECT * FROM comments WHERE reminderId = :reminderId ORDER BY createdAt, id")
    suspend fun byReminder(reminderId: Long): List<Comment>

    @Query(
        "SELECT c.reminderId AS reminderId, COUNT(*) AS count, " +
            "SUM(CASE WHEN c.authorName IS NOT NULL AND c.createdAt > r.commentsReadAt THEN 1 ELSE 0 END) AS unread " +
            "FROM comments c JOIN reminders r ON r.id = c.reminderId WHERE r.chatId = :chatId GROUP BY c.reminderId",
    )
    fun observeStats(chatId: Long): Flow<List<CommentStat>>

    @Insert
    suspend fun insert(comment: Comment): Long

    @Delete
    suspend fun delete(comment: Comment)
}

@Database(entities = [Chat::class, Reminder::class, Comment::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chats(): ChatDao
    abstract fun reminders(): ReminderDao
    abstract fun comments(): CommentDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nahadaika.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** 3.7: нагадування-будильник. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN alarm INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 3.16: обговорення під нагадуваннями; поля під спільні чати. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN authorName TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN commentsReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `comments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, `text` TEXT NOT NULL, `authorName` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "`remoteId` TEXT, FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_reminderId` ON `comments` (`reminderId`)")
            }
        }
    }
}
