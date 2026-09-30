package ua.nahadaika.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.theme.NahadaikaTheme

/** Запит відкрити конкретне нагадування (зі сповіщення). */
data class Focus(val chatId: Long, val reminderId: Long, val autoplay: Boolean, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private val focus = mutableStateOf<Focus?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            Repo.ensureDefaultChat()
            Repo.rescheduleAll()
        }
        setContent {
            NahadaikaTheme {
                AppRoot(focus.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
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
    }
}

@Composable
private fun AppRoot(focus: Focus?) {
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var activeFocus by remember { mutableStateOf<Focus?>(null) }

    LaunchedEffect(focus) {
        if (focus != null) {
            openChatId = focus.chatId
            activeFocus = focus
        }
    }

    val chatId = openChatId
    if (chatId == null) {
        ChatsScreen(onOpenChat = { openChatId = it })
    } else {
        ChatScreen(
            chatId = chatId,
            focus = activeFocus?.takeIf { it.chatId == chatId },
            onFocusConsumed = { activeFocus = null },
            onBack = {
                openChatId = null
                activeFocus = null
            },
        )
    }
}
