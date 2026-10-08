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

    /** «Розумний час» з голосу: null — ще не питали, true/false — вибір користувача. */
    fun smartVoice(context: Context): Boolean? =
        if (prefs(context).contains("smart_voice")) prefs(context).getBoolean("smart_voice", false) else null
    fun setSmartVoice(context: Context, on: Boolean) = prefs(context).edit().putBoolean("smart_voice", on).apply()

    /** Одноразова пропозиція ввійти через Google при першому запуску. */
    fun welcomeShown(context: Context): Boolean = prefs(context).getBoolean("welcome_shown", false)
    fun setWelcomeShown(context: Context) = prefs(context).edit().putBoolean("welcome_shown", true).apply()

    /** Ім'я, яке бачать учасники спільних чатів. */
    fun displayName(context: Context): String = prefs(context).getString("display_name", null).orEmpty()
    fun setDisplayName(context: Context, name: String) = prefs(context).edit().putString("display_name", name.trim()).apply()

    /** «Зберігати в хмарі»: усі чати мають копію у Firebase. */
    fun cloud(context: Context): Boolean = prefs(context).getBoolean("cloud", false)
    fun setCloud(context: Context, on: Boolean) = prefs(context).edit().putBoolean("cloud", on).apply()

    /** Чат справді спільний (є інші учасники), а не лише копія в хмарі. */
    fun isShared(context: Context, remoteChatId: String): Boolean = prefs(context).getBoolean("shared_$remoteChatId", false)
    fun setShared(context: Context, remoteChatId: String) = prefs(context).edit().putBoolean("shared_$remoteChatId", true).apply()

    /** Код запрошення спільного чату (щоб показати його знову без сервера). */
    fun inviteCode(context: Context, remoteChatId: String): String? = prefs(context).getString("invite_$remoteChatId", null)
    fun setInviteCode(context: Context, remoteChatId: String, code: String) =
        prefs(context).edit().putString("invite_$remoteChatId", code).apply()

    // ---- Оновлення ----

    fun autoUpdate(context: Context): Boolean = prefs(context).getBoolean("auto_update", true)
    fun setAutoUpdate(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto_update", on).apply()

    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong("last_update_check", 0)
    fun setLastUpdateCheck(context: Context, at: Long) = prefs(context).edit().putLong("last_update_check", at).apply()

    /** Про яку версію вже сповіщали, щоб не нагадувати щоразу. */
    fun notifiedUpdate(context: Context): Long = prefs(context).getLong("notified_update", 0)
    fun setNotifiedUpdate(context: Context, code: Long) = prefs(context).edit().putLong("notified_update", code).apply()
}
