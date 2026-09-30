package ua.nahadaika

import android.content.Context
import ua.nahadaika.data.Kind

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

    fun modeHintShown(context: Context): Boolean = prefs(context).getBoolean("mode_hint", false)
    fun setModeHintShown(context: Context) = prefs(context).edit().putBoolean("mode_hint", true).apply()
}
