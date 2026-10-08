package ua.nahadaika.share

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Кого запрошували поштою: пошта (мала літера) й ім'я для показу. */
data class Contact(val email: String, val name: String)

/**
 * Збережені адреси для запрошень у спільні чати — лише на цьому телефоні, у хмару не йдуть.
 * Порядок — від останнього використаного; зберігаємо не більше [MAX].
 */
object Contacts {
    const val MAX = 50
    private const val KEY = "contacts"

    private fun prefs(context: Context) = context.getSharedPreferences("invite_contacts", Context.MODE_PRIVATE)

    fun list(context: Context): List<Contact> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }
                .map { Contact(it.getString("email"), it.optString("name")) }
        }.getOrDefault(emptyList())
    }

    /** Додати або підняти нагору; ім'я не затираємо порожнім. */
    fun add(context: Context, email: String, name: String = "") {
        val clean = email.trim().lowercase()
        if (!isEmail(clean)) return
        val old = list(context)
        val keptName = name.trim().ifBlank { old.firstOrNull { it.email == clean }?.name.orEmpty() }
        save(context, (listOf(Contact(clean, keptName)) + old.filter { it.email != clean }).take(MAX))
    }

    fun remove(context: Context, email: String) = save(context, list(context).filter { it.email != email.trim().lowercase() })

    fun isEmail(text: String): Boolean = EMAIL.matches(text.trim())

    private fun save(context: Context, contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { array.put(JSONObject().put("email", it.email).put("name", it.name)) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    // Без android.util.Patterns: працює й у звичайних JVM-тестах.
    private val EMAIL = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$""")
}
