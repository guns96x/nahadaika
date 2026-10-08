package ua.nahadaika.share

import android.content.Context
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import com.google.android.gms.tasks.Task
import ua.nahadaika.BuildConfig
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Repeat
import java.io.File
import java.security.SecureRandom

/**
 * Чати в хмарі на Firestore. Структура:
 * chats/{id} — назва, учасники (members, names), код запрошення; chats/{id}/reminders, chats/{id}/comments;
 * chats/{id}/push — FCM-токени телефонів учасників; chats/{id}/media — медіа «кур'єром» (див. [MediaCourier]);
 * invites/{код} — до якого чату веде запрошення.
 * Якщо підключено Storage (план Blaze), медіа йде туди: chats/{chatId}/media/{reminderId}.ext, правила — storage.rules.
 */
class FirestoreBackend(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage? = null,
) : SharedBackend {
    private val courier = MediaCourier(context, auth, db)
    private val push = PushLink(context, auth, db)

    private suspend fun <T> Task<T>.confirmed(): T = withTimeout(20_000) { await() }
    // Відео до 25 МБ на повільному мобільному інтернеті за 20 с не встигає.
    private suspend fun <T> Task<T>.transferred(): T = withTimeout(5 * 60_000) { await() }
    private fun chat(id: String) = db.collection("chats").document(id)
    private fun reminders(chatId: String) = chat(chatId).collection("reminders")
    private fun comments(chatId: String) = chat(chatId).collection("comments")

    override suspend fun signIn(): String =
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().confirmed().user).uid

    override suspend fun signInWithGoogle(activity: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.FIREBASE_WEB_CLIENT_ID)
            .build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        val firebase = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        val current = auth.currentUser
        val user = try {
            // Анонімний вхід прив'язуємо до Google — ідентифікатор лишається, спільні чати не губляться.
            if (current != null && current.isAnonymous) current.linkWithCredential(firebase).confirmed().user
            else auth.signInWithCredential(firebase).confirmed().user
        } catch (e: FirebaseAuthUserCollisionException) {
            // Цей Google-акаунт уже є (інший телефон) — входимо в нього.
            auth.signInWithCredential(e.updatedCredential ?: firebase).confirmed().user
        }
        return checkNotNull(user).email.orEmpty()
    }

    override fun account(): String? = auth.currentUser?.takeUnless { it.isAnonymous }?.email

    override fun canUseGoogle(): Boolean = BuildConfig.FIREBASE_WEB_CLIENT_ID.isNotBlank()

    override fun newId(): String = db.collection("chats").document().id

    override suspend fun createChat(name: String, me: Member): RemoteChat {
        val ref = db.collection("chats").document()
        val code = newCode()
        ref.set(
            mapOf(
                "name" to name,
                "members" to listOf(me.uid),
                "names" to mapOf(me.uid to me.name),
                "owner" to me.uid,
                "inviteCode" to code,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).confirmed()
        db.collection("invites").document(code).set(mapOf("chatId" to ref.id, "createdBy" to me.uid)).confirmed()
        return RemoteChat(ref.id, name, code)
    }

    override suspend fun joinChat(code: String, me: Member): RemoteChat? {
        val invite = db.collection("invites").document(code).get().confirmed()
        val chatId = invite.getString("chatId") ?: return null
        chat(chatId).update(
            mapOf(
                "members" to FieldValue.arrayUnion(me.uid),
                "names.${me.uid}" to me.name,
                "joinCode" to code,
            ),
        ).confirmed()
        return toChat(chat(chatId).get().confirmed())?.copy(inviteCode = code)
    }

    override suspend fun myChats(): List<RemoteChat> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return db.collection("chats").whereArrayContains("members", uid).get().confirmed().documents.mapNotNull(::toChat)
    }

    override suspend fun renameChat(chatId: String, name: String) {
        chat(chatId).update("name", name).confirmed()
    }

    override suspend fun fetch(chatId: String): ChatSnapshot = ChatSnapshot(
        reminders(chatId).get(Source.SERVER).confirmed().documents.mapNotNull(::toReminder),
        comments(chatId).get(Source.SERVER).confirmed().documents.mapNotNull(::toComment),
        chat(chatId).get(Source.SERVER).confirmed().getString("name"),
    )

    override fun observe(chatId: String): Flow<ChatSnapshot> {
        val rs = callbackFlow {
            val reg = reminders(chatId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) close(error)
                if (snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()) trySend(snap.documents.mapNotNull(::toReminder))
            }
            awaitClose { reg.remove() }
        }
        val cs = callbackFlow {
            val reg = comments(chatId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) close(error)
                if (snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()) trySend(snap.documents.mapNotNull(::toComment))
            }
            awaitClose { reg.remove() }
        }
        val name = callbackFlow {
            val reg = chat(chatId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) close(error)
                if (snap != null && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()) trySend(snap.getString("name"))
            }
            awaitClose { reg.remove() }
        }
        return combine(rs, cs, name) { r, c, n -> ChatSnapshot(r, c, n) }
    }

    override suspend fun putReminder(chatId: String, reminder: RemoteReminder, create: Boolean) {
        val fields = mutableMapOf<String, Any>(
            "text" to reminder.text,
            "triggerAt" to reminder.triggerAt,
            "repeat" to reminder.repeat.name,
            "alarm" to reminder.alarm,
            "done" to reminder.done,
            "kind" to reminder.kind.name,
        )
        if (reminder.mediaRef != null) {
            fields["mediaRef"] = reminder.mediaRef
            fields["durationMs"] = reminder.durationMs
            fields["mediaSize"] = reminder.mediaSize
            reminder.mediaMime?.let { fields["mediaMime"] = it }
        }
        if (create) {
            fields["authorUid"] = reminder.authorUid
            fields["authorName"] = reminder.authorName
            fields["createdAt"] = reminder.createdAt
        }
        val document = reminders(chatId).document(reminder.id)
        // Оновлення не створює заново видалений на іншому телефоні запис.
        if (create) {
            document.set(fields, SetOptions.merge()).confirmed()
        } else {
            try {
                document.update(fields).confirmed()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) throw RemoteGone()
                throw e
            }
        }
    }

    override suspend fun deleteReminder(chatId: String, reminderId: String) {
        reminders(chatId).document(reminderId).delete().confirmed()
        runCatching { courier.deleteFor(chatId, reminderId) }
    }

    override suspend fun putComment(chatId: String, comment: RemoteComment) {
        val fields = mapOf(
                "reminderId" to comment.reminderId,
                "text" to comment.text,
                "authorUid" to comment.authorUid,
                "authorName" to comment.authorName,
                "createdAt" to comment.createdAt,
            )
        val document = comments(chatId).document(comment.id)
        val existing = document.get(Source.SERVER).confirmed()
        if (existing.exists()) {
            check(existing.data == fields) { "Коментар із цим ID уже існує" }
            return
        }
        document.set(fields).confirmed()
    }

    override suspend fun leave(chatId: String, me: Member) {
        runCatching { push.unregister(chatId) }
        val members = chat(chatId).get().confirmed().get("members") as? List<*>
        if (members == listOf(me.uid)) {
            chat(chatId).delete().confirmed()
        } else {
            chat(chatId).update(mapOf("members" to FieldValue.arrayRemove(me.uid), "names.${me.uid}" to FieldValue.delete())).confirmed()
        }
    }

    // Без Storage медіа везе кур'єр через Firestore.
    override fun storageAvailable(): Boolean = true

    override fun mediaIsTemporary(): Boolean = storage == null

    override suspend fun uploadMedia(chatId: String, reminderId: String, file: File, mime: String): UploadedMedia? {
        val (send, type) = MediaShrink.prepare(context, file, mime, reminderId)
        try {
            // Завелике навіть після стиснення — лишається на телефоні автора.
            if (send.length() !in 1..MediaSync.MAX_MEDIA_SIZE_BYTES) return null
            val st = storage ?: return courier.upload(chatId, reminderId, send, type)
            val path = MediaSync.storagePath(chatId, reminderId, send.extension.ifEmpty { "bin" })
            check(MediaSync.isSafeMediaRef(path))
            val metadata = StorageMetadata.Builder().setContentType(type).build()
            st.reference.child(path).putFile(Uri.fromFile(send), metadata).transferred()
            return UploadedMedia(path, send.length(), type)
        } finally {
            if (send != file) send.delete()
        }
    }

    override suspend fun downloadMedia(mediaRef: String, target: File): Boolean {
        check(MediaSync.isSafeMediaRef(mediaRef))
        courier.download(mediaRef, target)?.let { return it }
        val st = storage ?: run {
            courier.markGone(mediaRef)
            return false
        }
        val ref = st.reference.child(mediaRef)
        val metadata = ref.metadata.confirmed()
        check(metadata.sizeBytes in 1..MediaSync.MAX_MEDIA_SIZE_BYTES)
        ref.getFile(target).transferred()
        return true
    }

    override suspend fun mediaReceived(chatId: String, mediaRef: String) = courier.received(chatId, mediaRef)

    override suspend fun cleanupMedia(chatId: String) = courier.cleanup(chatId)

    override suspend fun registerPush(chatId: String) = push.register(chatId)

    override suspend fun staleMembers(chatId: String): List<String> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val info = chat(chatId).get(Source.SERVER).confirmed()
        val members = (info.get("members") as? List<*>).orEmpty().filterIsInstance<String>()
        val names = (info.get("names") as? Map<*, *>).orEmpty()
        val current = chat(chatId).collection("push").get(Source.SERVER).confirmed().documents
            .filter { (it.getLong("app") ?: 0) >= PushLink.MIN_VERSION }
            .mapNotNull { it.getString("uid") }
            .toSet()
        return members.filter { it != me && it !in current }.map { (names[it] as? String).orEmpty().ifBlank { "?" } }
    }

    override fun updateLink(): String = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"

    override suspend fun notifyMembers(chatId: String, urgent: Boolean) = push.notify(chatId, urgent)

    private fun toChat(d: DocumentSnapshot): RemoteChat? {
        if (!d.exists()) return null
        return RemoteChat(
            id = d.id,
            name = d.getString("name").orEmpty(),
            inviteCode = d.getString("inviteCode").orEmpty(),
            members = (d.get("members") as? List<*>)?.size ?: 1,
        )
    }

    private fun toReminder(d: DocumentSnapshot): RemoteReminder? {
        val triggerAt = d.getLong("triggerAt") ?: return null
        val kindStr = d.getString("kind")
        val kind = if (kindStr != null) {
            runCatching { Kind.valueOf(kindStr) }.getOrDefault(Kind.TEXT)
        } else Kind.TEXT
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
            kind = kind,
            mediaRef = d.getString("mediaRef"),
            durationMs = d.getLong("durationMs") ?: 0,
            mediaSize = d.getLong("mediaSize") ?: 0,
            mediaMime = d.getString("mediaMime"),
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
