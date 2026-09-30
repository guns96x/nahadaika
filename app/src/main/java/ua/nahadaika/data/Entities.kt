package ua.nahadaika.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** "Чат" — тема, у яку складаються нагадування (як окремий чат у Telegram). */
@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class Kind { TEXT, VOICE, VIDEO, PHOTO }

enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Відкладене повідомлення-нагадування. */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chatId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val kind: Kind,
    /** Текст повідомлення або підпис до медіа. */
    val text: String = "",
    /** Абсолютний шлях до файлу у внутрішньому сховищі застосунку. */
    val mediaPath: String? = null,
    val durationMs: Long = 0,
    /** Запланований час (для повторюваних — найближче повторення). */
    val triggerAt: Long,
    val repeat: Repeat = Repeat.NONE,
    /** Якщо нагадування відкладено кнопкою "+10 хв" / "+1 год". */
    val snoozedUntil: Long? = null,
    /** Одноразове нагадування вже надійшло (лежить в історії). */
    val fired: Boolean = false,
    val lastFiredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Будильник: гучна мелодія, поки не вимкнуть, і екран на весь дисплей. */
    @ColumnInfo(defaultValue = "0") val alarm: Boolean = false,
)

/** Час, на який реально стоїть будильник. */
fun Reminder.alarmAt(): Long = snoozedUntil ?: triggerAt
