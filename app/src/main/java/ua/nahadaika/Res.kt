package ua.nahadaika

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Рядки для коду поза Compose (сповіщення, форматування, Room-репозиторій).
 * У Compose — `stringResource` / `pluralStringResource`. Мова — системна (на Android 13+ — вибрана для застосунку).
 */
object Res {
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun s(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        app.resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf(count) else args))
}
