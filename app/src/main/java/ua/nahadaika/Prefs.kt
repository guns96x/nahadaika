package ua.nahadaika

import android.content.Context
import ua.nahadaika.data.Kind
import java.time.LocalTime

/** Дрібні налаштування, які застосунок запам'ятовує між запусками. */
object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun lastChatId(context: Context): Long = prefs(context).getLong("last_chat", -1)
    fun setLastChatId(context: Context, id: Long) = prefs(context).edit().putLong("last_chat", id).apply()

    /** Режим великої кнопки запису: відео (за замовчуванням) або голосове. */
    fun recordMode(context: Context): Kind =
        if (prefs(context).getString("record_mode", null) == Kind.VOICE.name) Kind.VOICE else Kind.VIDEO
    fun setRecordMode(context: Context, kind: Kind) = prefs(context).edit().putString("record_mode", kind.name).apply()

    fun frontCamera(context: Context): Boolean = prefs(context).getBoolean("front_camera", true)
    fun setFrontCamera(context: Context, front: Boolean) = prefs(context).edit().putBoolean("front_camera", front).apply()

    fun gestureHintShown(context: Context): Boolean = prefs(context).getBoolean("gesture_hint", false)
    fun setGestureHintShown(context: Context) = prefs(context).edit().putBoolean("gesture_hint", true).apply()

    /** Вкладка у вікні вибору часу: 0 — «Через» (таймер), 1 — «Коли» (дата й час). */
    fun scheduleMode(context: Context): Int = prefs(context).getInt("schedule_mode", 1)
    fun setScheduleMode(context: Context, mode: Int) = prefs(context).edit().putInt("schedule_mode", mode).apply()

    /** Останнє значення таймера «через», у секундах. */
    fun timerSeconds(context: Context): Int = prefs(context).getInt("timer_seconds", 30 * 60)
    fun setTimerSeconds(context: Context, seconds: Int) = prefs(context).edit().putInt("timer_seconds", seconds).apply()

    /** Тема: AUTO / DARK / LIGHT (назва з ThemeMode). */
    fun themeMode(context: Context): String = prefs(context).getString("theme_mode", null) ?: "LIGHT"
    fun setThemeMode(context: Context, mode: String) = prefs(context).edit().putString("theme_mode", mode).apply()

    /** Перша кнопка «відкласти» у сповіщенні, хвилин. */
    fun snoozeMinutes(context: Context): Int = prefs(context).getInt("snooze_minutes", 10)
    fun setSnoozeMinutes(context: Context, minutes: Int) = prefs(context).edit().putInt("snooze_minutes", minutes).apply()

    /** Година за замовчуванням, коли на смужці обрано інший день. */
    fun defaultHour(context: Context): Int = prefs(context).getInt("default_hour", 9)
    fun setDefaultHour(context: Context, hour: Int) = prefs(context).edit().putInt("default_hour", hour).apply()
    fun defaultTime(context: Context): LocalTime = LocalTime.of(defaultHour(context), 0)

    // ---- Оновлення ----

    fun autoUpdate(context: Context): Boolean = prefs(context).getBoolean("auto_update", true)
    fun setAutoUpdate(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto_update", on).apply()

    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong("last_update_check", 0)
    fun setLastUpdateCheck(context: Context, at: Long) = prefs(context).edit().putLong("last_update_check", at).apply()

    /** Про яку версію вже сповіщали, щоб не нагадувати щоразу. */
    fun notifiedUpdate(context: Context): Long = prefs(context).getLong("notified_update", 0)
    fun setNotifiedUpdate(context: Context, code: Long) = prefs(context).edit().putLong("notified_update", code).apply()
}
