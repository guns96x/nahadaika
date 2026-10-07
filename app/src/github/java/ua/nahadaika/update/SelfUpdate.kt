package ua.nahadaika.update

import android.content.Context
import ua.nahadaika.Prefs

/** Точка входу самооновлення для решти коду; у варіанті play така ж, але порожня. */
object SelfUpdate {
    fun init(context: Context) = UpdateWorker.schedule(context, Prefs.autoUpdate(context))

    /** Тиха перевірка при відкритті застосунку — не частіше ніж раз на 6 годин. */
    fun checkOnLaunch(context: Context) {
        if (Prefs.autoUpdate(context) && System.currentTimeMillis() - Prefs.lastUpdateCheck(context) > 6 * 3_600_000L) {
            Updates.check(context, silent = true)
        }
    }
}
