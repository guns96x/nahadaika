package ua.nahadaika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.nahadaika.data.Repo
import ua.nahadaika.share.Contacts
import ua.nahadaika.share.SharedChats

/** Збережені адреси для запрошень і перевірка «хто ще не оновився». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val backend = FakeBackend()

    @Before fun setUp() {
        Repo.init(app)
        app.getSharedPreferences("invite_contacts", 0).edit().clear().commit()
        SharedChats.init(app, backend, watch = false)
    }

    @After fun tearDown() = SharedChats.reset()

    @Test fun addsLowercasedAndRaisesExistingToTop() {
        Contacts.add(app, " Mama@Gmail.com ", "Мама")
        Contacts.add(app, "tato@ukr.net")
        Contacts.add(app, "mama@gmail.com") // піднімається нагору, ім'я не затирається
        assertEquals(listOf("mama@gmail.com", "tato@ukr.net"), Contacts.list(app).map { it.email })
        assertEquals("Мама", Contacts.list(app).first().name)
    }

    @Test fun rejectsInvalidAddresses() {
        listOf("", "abc", "a@b", "a b@c.com", "@c.com").forEach { Contacts.add(app, it) }
        assertTrue(Contacts.list(app).isEmpty())
        assertTrue(Contacts.isEmail("ok.name+tag@mail.example.ua"))
        assertFalse(Contacts.isEmail("no-at.example.com"))
    }

    @Test fun keepsOnlyFiftyMostRecent() {
        repeat(Contacts.MAX + 5) { Contacts.add(app, "u$it@mail.com") }
        val list = Contacts.list(app)
        assertEquals(Contacts.MAX, list.size)
        assertEquals("u${Contacts.MAX + 4}@mail.com", list.first().email)
    }

    @Test fun removesIgnoringCase() {
        Contacts.add(app, "a@mail.com")
        Contacts.add(app, "b@mail.com")
        Contacts.remove(app, "A@Mail.com")
        assertEquals(listOf("b@mail.com"), Contacts.list(app).map { it.email })
    }

    @Test fun staleCheckPublishesNamesAndIsThrottled() = runBlocking {
        backend.stale = listOf("Мама")
        SharedChats.checkStale("c1")
        assertEquals(listOf("Мама"), SharedChats.stale.value["c1"])
        backend.stale = emptyList()
        SharedChats.checkStale("c1") // менше години — не питаємо знову
        assertEquals(1, backend.staleChecks)
        assertEquals(listOf("Мама"), SharedChats.stale.value["c1"])
        SharedChats.checkStale("c1", force = true)
        assertEquals(2, backend.staleChecks)
        assertTrue(SharedChats.stale.value["c1"].orEmpty().isEmpty())
    }

    @Test fun staleCheckFailureKeepsStateAndRetriesLater() = runBlocking {
        backend.failStale = true
        SharedChats.checkStale("c1")
        assertTrue(SharedChats.stale.value["c1"] == null)
        backend.failStale = false
        backend.stale = listOf("Тато")
        SharedChats.checkStale("c1") // збій не вважається перевіркою
        assertEquals(listOf("Тато"), SharedChats.stale.value["c1"])
    }
}
