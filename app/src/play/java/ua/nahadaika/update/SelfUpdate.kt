package ua.nahadaika.update

import android.content.Context

/** У Play застосунок оновлює лише Play, тож самооновлення тут немає (див. варіант github). */
@Suppress("UNUSED_PARAMETER")
object SelfUpdate {
    fun init(context: Context) = Unit
    fun checkOnLaunch(context: Context) = Unit
}
