package ua.nahadaika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.nahadaika.data.Repo
import ua.nahadaika.share.Member
import ua.nahadaika.share.SharedChats

/** Запрошення за Google-поштою: надсилання, отримання в застосунку, прийняття й відхилення. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MailInviteTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val server = FakeBackend()

    @Before fun setUp() {
        Repo.init(app)
        Prefs.setDisplayName(app, "Мама")
        SharedChats.init(app, server, watch = false)
    }

    @After fun tearDown() = SharedChats.reset()

    /** Чат і запрошення, створені «чужим» телефоном напряму на сервері-підміні. */
    private suspend fun inviteFromElsewhere(email: String) {
        val remote = server.createChat("Сім'я", Member("mama-uid", "Мама"))
        server.inviteByEmail(remote.id, "Сім'я", remote.inviteCode, email, "Мама")
    }

    @Test fun sendingStoresInviteForLowercasedEmail() = runBlocking {
        val chat = Repo.chatById(Repo.createChat("Сім'я"))!!
        assertTrue(SharedChats.inviteByEmail(chat, "  Tato@Gmail.com "))
        val (email, invite) = server.mailbox.values.single()
        assertEquals("tato@gmail.com", email)
        assertEquals("Сім'я", invite.chatName)
        assertEquals("Мама", invite.fromName)
    }

    @Test fun reportsWhenServerCouldNotMail() = runBlocking {
        server.mailServerWorks = false
        val chat = Repo.chatById(Repo.createChat("Сім'я"))!!
        assertTrue(!SharedChats.inviteByEmail(chat, "tato@gmail.com"))
        assertEquals(1, server.mailbox.size) // запрошення в застосунку все одно є
    }

    @Test fun invitedPersonSeesInviteAndAcceptsIt() = runBlocking {
        inviteFromElsewhere("tato@gmail.com")
        server.email = "tato@gmail.com"
        SharedChats.refreshInbox()
        val invite = SharedChats.inbox.value.single()
        assertEquals("Сім'я", invite.chatName)

        assertNotNull(SharedChats.acceptInvite(invite))
        assertTrue(SharedChats.inbox.value.isEmpty())
        assertTrue(server.mailbox.isEmpty())
    }

    @Test fun strangersSeeNothing() = runBlocking {
        inviteFromElsewhere("tato@gmail.com")
        server.email = "other@gmail.com"
        SharedChats.refreshInbox()
        assertTrue(SharedChats.inbox.value.isEmpty())
    }

    @Test fun withoutGoogleSignInThereIsNoInbox() = runBlocking {
        inviteFromElsewhere("tato@gmail.com")
        server.email = null
        SharedChats.refreshInbox()
        assertTrue(SharedChats.inbox.value.isEmpty())
    }

    @Test fun declineRemovesInvite() = runBlocking {
        inviteFromElsewhere("tato@gmail.com")
        server.email = "tato@gmail.com"
        SharedChats.refreshInbox()
        SharedChats.declineInvite(SharedChats.inbox.value.single())
        assertTrue(SharedChats.inbox.value.isEmpty())
        assertTrue(server.mailbox.isEmpty())
    }
}
