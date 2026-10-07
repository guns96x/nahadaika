package ua.nahadaika.share

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import ua.nahadaika.data.Repeat
import java.security.SecureRandom

/**
 * Спільні чати на Firestore. Структура:
 * chats/{id} — назва, учасники (members, names); chats/{id}/reminders, chats/{id}/comments;
 * invites/{код} — до якого чату веде запрошення. Правила доступу — firebase/firestore.rules.
 */
class FirestoreBackend(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : SharedBackend {
    private fun chat(id: String) = db.collection("chats").document(id)
    private fun reminders(chatId: String) = chat(chatId).collection("reminders")
    private fun comments(chatId: String) = chat(chatId).collection("comments")

    override suspend fun signIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid

    override fun newId(): String = db.collection("chats").document().id

    override suspend fun createChat(name: String, me: Member): RemoteChat {
        val ref = db.collection("chats").document()
        ref.set(
            mapOf(
                "name" to name,
                "members" to listOf(me.uid),
                "names" to mapOf(me.uid to me.name),
                "owner" to me.uid,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        val code = newCode()
        db.collection("invites").document(code).set(mapOf("chatId" to ref.id, "createdBy" to me.uid)).await()
        return RemoteChat(ref.id, name, code)
    }

    override suspend fun joinChat(code: String, me: Member): RemoteChat? {
        val invite = db.collection("invites").document(code).get().await()
        val chatId = invite.getString("chatId") ?: return null
        chat(chatId).update(
            mapOf(
                "members" to FieldValue.arrayUnion(me.uid),
                "names.${me.uid}" to me.name,
                "joinCode" to code,
            ),
        ).await()
        val name = chat(chatId).get().await().getString("name").orEmpty()
        return RemoteChat(chatId, name, code)
    }

    override suspend fun fetch(chatId: String): ChatSnapshot = ChatSnapshot(
        reminders(chatId).get().await().documents.mapNotNull(::toReminder),
        comments(chatId).get().await().documents.mapNotNull { toComment(it) },
    )

    override fun observe(chatId: String): Flow<ChatSnapshot> {
        val rs = callbackFlow {
            val reg = reminders(chatId).addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.mapNotNull(::toReminder))
            }
            awaitClose { reg.remove() }
        }
        val cs = callbackFlow {
            val reg = comments(chatId).addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.mapNotNull { toComment(it) })
            }
            awaitClose { reg.remove() }
        }
        return combine(rs, cs) { r, c -> ChatSnapshot(r, c) }
    }

    override suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean) {
        val fields = mutableMapOf<String, Any>(
            "text" to reminder.text,
            "triggerAt" to reminder.triggerAt,
            "repeat" to reminder.repeat.name,
            "alarm" to reminder.alarm,
            "done" to reminder.done,
        )
        if (create) {
            fields["authorUid"] = reminder.authorUid
            fields["authorName"] = reminder.authorName
            fields["createdAt"] = reminder.createdAt
        }
        // Без await: Firestore сам донадішле, коли з'явиться інтернет, а локальні слухачі бачать зміну одразу.
        reminders(chatId).document(reminder.id).set(fields, SetOptions.merge())
    }

    override suspend fun deleteReminder(chatId: String, reminderId: String) {
        reminders(chatId).document(reminderId).delete()
    }

    override suspend fun putComment(chatId: String, comment: RemoteComment) {
        comments(chatId).document(comment.id).set(
            mapOf(
                "reminderId" to comment.reminderId,
                "text" to comment.text,
                "authorUid" to comment.authorUid,
                "authorName" to comment.authorName,
                "createdAt" to comment.createdAt,
            ),
        )
    }

    override suspend fun leave(chatId: String, me: Member) {
        chat(chatId).update(mapOf("members" to FieldValue.arrayRemove(me.uid), "names.${me.uid}" to FieldValue.delete())).await()
    }

    private fun toReminder(d: DocumentSnapshot): RemoteReminder? {
        val triggerAt = d.getLong("triggerAt") ?: return null
        return RemoteReminder(
            id = d.id,
            text = d.getString("text").orEmpty(),
            triggerAt = triggerAt,
            repeat = runCatching { Repeat.valueOf(d.getString("repeat").orEmpty()) }.getOrDefault(Repeat.NONE),
            alarm = d.getBoolean("alarm") ?: false,
            done = d.getBoolean("done") ?: false,
            authorUid = d.getString("authorUid").orEmpty(),
            authorName = d.getString("authorName").orEmpty(),
            createdAt = d.getLong("createdAt") ?: 0,
        )
    }

    private fun toComment(d: DocumentSnapshot): RemoteComment? {
        val reminderId = d.getString("reminderId") ?: return null
        return RemoteComment(
            id = d.id,
            reminderId = reminderId,
            text = d.getString("text").orEmpty(),
            authorUid = d.getString("authorUid").orEmpty(),
            authorName = d.getString("authorName").orEmpty(),
            createdAt = d.getLong("createdAt") ?: 0,
        )
    }

    private companion object {
        /** Без схожих символів (0/O, 1/I/L), щоб код легко продиктувати. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()

        fun newCode(): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
