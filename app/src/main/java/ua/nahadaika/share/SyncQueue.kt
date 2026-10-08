package ua.nahadaika.share

import android.content.Context
import android.content.SharedPreferences

/**
 * Черга відкладених дій офлайн для захисту від воскресіння видаленого,
 * втрати створеного та забезпечення повторних спроб синхронізації.
 */
object SyncQueue {
    private const val PREFS_NAME = "shared_sync_queue"
    private const val KEY_PENDING_DELETIONS = "pending_deletions"
    private const val KEY_PENDING_UPDATES = "pending_updates"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun recordPendingDeletion(context: Context, chatRemoteId: String, reminderRemoteId: String) {
        val p = prefs(context)
        val current = p.getStringSet(KEY_PENDING_DELETIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add("$chatRemoteId:$reminderRemoteId")
        p.edit().putStringSet(KEY_PENDING_DELETIONS, current).commit()
    }

    @Synchronized
    fun isPendingDeletion(context: Context, chatRemoteId: String, reminderRemoteId: String): Boolean {
        val current = prefs(context).getStringSet(KEY_PENDING_DELETIONS, emptySet()) ?: emptySet()
        return "$chatRemoteId:$reminderRemoteId" in current
    }

    @Synchronized
    fun removePendingDeletion(context: Context, chatRemoteId: String, reminderRemoteId: String) {
        val p = prefs(context)
        val current = p.getStringSet(KEY_PENDING_DELETIONS, emptySet())?.toMutableSet() ?: return
        if (current.remove("$chatRemoteId:$reminderRemoteId")) {
            p.edit().putStringSet(KEY_PENDING_DELETIONS, current).commit()
        }
    }

    @Synchronized
    fun allPendingDeletions(context: Context): List<Pair<String, String>> {
        val current = prefs(context).getStringSet(KEY_PENDING_DELETIONS, emptySet()) ?: emptySet()
        return current.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    @Synchronized
    fun recordPendingUpdate(context: Context, reminderId: Long) {
        val p = prefs(context)
        val current = p.getStringSet(KEY_PENDING_UPDATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(reminderId.toString())
        p.edit().putStringSet(KEY_PENDING_UPDATES, current)
            .putLong("revision_$reminderId", p.getLong("revision_$reminderId", 0) + 1).commit()
    }

    @Synchronized
    fun removePendingUpdate(context: Context, reminderId: Long, expectedRevision: Long? = null) {
        val p = prefs(context)
        if (expectedRevision != null && p.getLong("revision_$reminderId", 0) != expectedRevision) return
        val current = p.getStringSet(KEY_PENDING_UPDATES, emptySet())?.toMutableSet() ?: return
        if (current.remove(reminderId.toString())) {
            p.edit().putStringSet(KEY_PENDING_UPDATES, current).commit()
        }
    }

    @Synchronized
    fun allPendingUpdates(context: Context): Set<Long> {
        val current = prefs(context).getStringSet(KEY_PENDING_UPDATES, emptySet()) ?: emptySet()
        return current.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun isPendingUpdate(context: Context, reminderId: Long): Boolean = reminderId in allPendingUpdates(context)

    fun revision(context: Context, reminderId: Long): Long = prefs(context).getLong("revision_$reminderId", 0)

    fun pendingRemoteId(context: Context, reminderId: Long): String? = prefs(context).getString("reminder_$reminderId", null)
    fun pendingCommentId(context: Context, commentId: Long): String? = prefs(context).getString("comment_$commentId", null)

    /** Повтор невідомого результату запису використовує той самий ID, зокрема після перезапуску. */
    @Synchronized
    fun reserveId(context: Context, type: String, localId: Long, create: () -> String): String {
        val p = prefs(context)
        val key = "${type}_$localId"
        return p.getString(key, null) ?: create().also { p.edit().putString(key, it).commit() }
    }

    fun clearReservation(context: Context, type: String, localId: Long) {
        prefs(context).edit().remove("${type}_$localId").commit()
    }

    /** Медіа, яке сервер не прийняв (завелике): лишається на телефоні, повторно не відправляється. */
    fun markLocalOnly(context: Context, reminderId: Long) {
        prefs(context).edit().putBoolean("local_only_$reminderId", true).commit()
    }

    fun isLocalOnly(context: Context, reminderId: Long): Boolean = prefs(context).getBoolean("local_only_$reminderId", false)

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
