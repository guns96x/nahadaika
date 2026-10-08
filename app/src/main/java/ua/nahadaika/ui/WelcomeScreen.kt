package ua.nahadaika.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ua.nahadaika.Prefs
import ua.nahadaika.R
import ua.nahadaika.share.SharedChats
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.PrimaryButton
import ua.nahadaika.ui.theme.PrimaryCircle
import ua.nahadaika.ui.theme.card

/** Один раз на першому запуску: увійти через Google (копія чатів у хмарі й запрошення від інших). */
fun shouldShowWelcome(context: android.content.Context): Boolean =
    SharedChats.cloudSupported() && !SharedChats.cloudEnabled() && !Prefs.welcomeShown(context)

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun finish() {
        Prefs.setWelcomeShown(context)
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PrimaryCircle(onClick = {}, size = 72.dp) { Icon(Icons.Default.Group, null, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.welcome_title), color = Glass.Text, fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(Icons.Default.Cloud, stringResource(R.string.welcome_cloud))
                Point(Icons.Default.Group, stringResource(R.string.welcome_invites))
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = stringResource(R.string.welcome_google),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { SharedChats.enableCloud(context) }
                            .onSuccess { finish() }
                            .onFailure { Toast.makeText(context, context.getString(R.string.settings_cloud_failed), Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                },
            )
            TextButton(onClick = ::finish, enabled = !busy) {
                Text(stringResource(R.string.welcome_later), color = Glass.TextDim)
            }
        }
    }
}

@Composable
private fun Point(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Glass.Lavender, modifier = Modifier.size(22.dp))
        Text(text, color = Glass.Text, fontSize = 15.sp, lineHeight = 21.sp)
    }
}
