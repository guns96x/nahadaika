package ua.nahadaika.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY createdAt")
    fun observeAll(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: Long): Flow<Chat?>

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

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Database(entities = [Chat::class, Reminder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chats(): ChatDao
    abstract fun reminders(): ReminderDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nahadaika.db").build()
    }
}
