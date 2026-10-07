package ua.nahadaika.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.nahadaika.R
import ua.nahadaika.Res
import ua.nahadaika.data.Repo
import ua.nahadaika.formatTime
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.NahadaikaTheme

/** Будильник на весь екран — з'являється навіть на заблокованому телефоні. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        Repo.init(this)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: Res.s(R.string.core_alarm_default_title)
        val chat = intent.getStringExtra(EXTRA_CHAT) ?: ""
        setContent {
            NahadaikaTheme {
                AlarmScreen(
                    title = title,
                    chat = chat,
                    onSnooze = {
                        lifecycleScope.launch {
                            if (id >= 0) Repo.snooze(id, 5)
                            finish()
                        }
                    },
                    onStop = {
                        if (id >= 0) Notifier.cancel(this, id)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHAT = "chat"
    }
}

@Composable
fun AlarmScreen(title: String, chat: String, onSnooze: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val pulse by rememberInfiniteTransition(label = "alarm").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(132.dp).scale(pulse).clip(CircleShape).background(Glass.Fill).border(1.dp, Glass.Stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Alarm, null, tint = Glass.Lavender, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(36.dp))
            Text(formatTime(now), color = Glass.Text, fontSize = 84.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(title, color = Glass.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (chat.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(chat, color = Glass.TextDim, fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AlarmButton(stringResource(R.string.core_alarm_snooze_5min), primary = false, onClick = onSnooze, modifier = Modifier.weight(1f))
                AlarmButton(stringResource(R.string.core_alarm_dismiss), primary = true, onClick = onStop, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlarmButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(64.dp)
            .clip(Glass.Pill)
            .background(if (primary) Glass.Primary else Glass.Fill)
            .border(0.8.dp, Glass.Stroke, Glass.Pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Glass.OnPrimary else Glass.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
