package ua.nahadaika.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ua.nahadaika.Prefs
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.theme.NahadaikaTheme
import ua.nahadaika.ui.theme.isDarkTheme

/** Запит відкрити конкретне нагадування (зі сповіщення). */
data class Focus(val chatId: Long, val reminderId: Long, val autoplay: Boolean, val nonce: Long = System.nanoTime())

/** Швидка дія з ярлика на головному екрані. */
enum class QuickAction { VIDEO, VOICE, DICTATE }
data class Quick(val action: QuickAction, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private val focus = mutableStateOf<Focus?>(null)
    private val quick = mutableStateOf<Quick?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            Repo.ensureDefaultChat()
            Repo.rescheduleAll()
        }
        setContent {
            // Колір іконок у системних панелях — під обрану тему.
            val dark = isDarkTheme()
            SideEffect {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NahadaikaTheme {
                AppRoot(focus.value, quick.value, onQuickConsumed = { quick.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_QUICK_VIDEO -> quick.value = Quick(QuickAction.VIDEO)
            ACTION_QUICK_VOICE -> quick.value = Quick(QuickAction.VOICE)
            ACTION_QUICK_DICTATE -> quick.value = Quick(QuickAction.DICTATE)
        }
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1)
        if (chatId <= 0) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        focus.value = Focus(chatId, reminderId, intent.getBooleanExtra(EXTRA_AUTOPLAY, false))
        // Натискання на сповіщення = "прочитано".
        Notifier.cancel(this, reminderId)
    }

    companion object {
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_AUTOPLAY = "autoplay"
        const val ACTION_QUICK_VIDEO = "ua.nahadaika.QUICK_VIDEO"
        const val ACTION_QUICK_VOICE = "ua.nahadaika.QUICK_VOICE"
        const val ACTION_QUICK_DICTATE = "ua.nahadaika.QUICK_DICTATE"
    }
}

/** Застосунок одразу відкривається в останньому чаті; список чатів — окремим екраном. */
@Composable
private fun AppRoot(focus: Focus?, quick: Quick?, onQuickConsumed: () -> Unit) {
    val context = LocalContext.current
    val chats by Repo.chats.collectAsStateWithLifecycle(null)
    var chatId by rememberSaveable { mutableLongStateOf(Prefs.lastChatId(context)) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var activeFocus by remember { mutableStateOf<Focus?>(null) }

    LaunchedEffect(focus) {
        if (focus != null) {
            chatId = focus.chatId
            showList = false
            showSettings = false
            activeFocus = focus
        }
    }
    LaunchedEffect(quick) {
        if (quick != null) {
            showList = false
            showSettings = false
        }
    }

    val list = chats ?: return
    val current = list.find { it.id == chatId } ?: list.firstOrNull()
    LaunchedEffect(list.isEmpty()) {
        if (list.isEmpty()) Repo.ensureDefaultChat()
    }
    LaunchedEffect(current?.id) {
        current?.let { Prefs.setLastChatId(context, it.id) }
    }
    if (current == null) return

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else if (showList) {
        ChatsScreen(
            onOpenSettings = { showSettings = true },
            onOpenChat = {
                chatId = it
                showList = false
            },
            onBack = { showList = false },
        )
    } else {
        ChatScreen(
            chatId = current.id,
            focus = activeFocus?.takeIf { it.chatId == current.id },
            onFocusConsumed = { activeFocus = null },
            quick = quick,
            onQuickConsumed = onQuickConsumed,
            onOpenChats = { showList = true },
        )
    }
}
