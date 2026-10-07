package ua.nahadaika.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.nahadaika.Prefs
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.update.UpdateWorker
import ua.nahadaika.update.Updates

// Самооновлення з GitHub Releases — лише у варіанті github (Play забороняє оновлення в обхід магазину).

/** Нова версія застосунку: «Оновити» одним натиском, далі — хід завантаження. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    when (val st = Updates.state) {
        is Updates.State.Available -> Banner(
            icon = Icons.Default.SystemUpdate,
            text = "Доступна нова версія ${st.info.versionName}",
            action = "Оновити",
            onAction = { Updates.download(context, st.info) },
        )
        is Updates.State.Downloading -> Banner(
            icon = Icons.Default.SystemUpdate,
            text = "Оновлення… ${(st.progress * 100).toInt()}%",
            action = "",
            onAction = {},
        )
        is Updates.State.ReadyToInstall -> Banner(
            icon = Icons.Default.SystemUpdate,
            text = "Версія ${st.info.versionName} завантажена",
            action = "Встановити",
            onAction = { Updates.install(context, st.info, st.file) },
        )
        else -> Unit
    }
}

/** Налаштування: версія, перевірка оновлень, автоперевірка. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdate(context)) }
    Section("Оновлення") {
        UpdateRow()
        Divider()
        SwitchRow(
            Icons.Default.Autorenew,
            "Перевіряти автоматично",
            "Раз на пів дня; про нову версію прийде сповіщення",
            autoUpdate,
        ) {
            autoUpdate = it
            Prefs.setAutoUpdate(context, it)
            UpdateWorker.schedule(context, it)
        }
    }
}

private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576f).replace('.', ',') + " МБ"

/** Поточна версія, кнопка «Перевірити оновлення» і хід оновлення. */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val current = remember { Updates.currentVersionName(context) }
    val st = Updates.state
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Default.SystemUpdate, if (st is Updates.State.Available) Glass.Lavender else Glass.TextDim)
            Spacer(Modifier.width(16.dp))
            val (title, subtitle) = when (st) {
                Updates.State.Idle -> "Версія $current" to "Натисніть, щоб перевірити"
                Updates.State.Checking -> "Версія $current" to "Перевіряю…"
                Updates.State.UpToDate -> "Версія $current" to "Це найновіша версія ✓"
                Updates.State.NoReleases -> "Версія $current" to "На GitHub ще немає опублікованих версій"
                is Updates.State.Available -> "Доступна версія ${st.info.versionName}" to
                    if (st.info.patch != null) "Завантажити ${mb(st.info.downloadSize)} замість ${mb(st.info.apk.size)}"
                    else "Завантажити ${mb(st.info.apk.size)}"
                is Updates.State.Downloading -> "Оновлення до ${st.info.versionName}" to
                    "${if (st.patch) "Патч" else "Повний APK"} · ${(st.progress * 100).toInt()}%"
                is Updates.State.ReadyToInstall -> "Версія ${st.info.versionName} готова" to "Дозвольте встановлення й натисніть «Встановити»"
                is Updates.State.Failed -> "Версія $current" to st.message
            }
            Titles(title, subtitle, Modifier.weight(1f))
            val (label, action) = when (st) {
                Updates.State.Idle, Updates.State.UpToDate, Updates.State.NoReleases -> "Перевірити" to { Updates.check(context) }
                Updates.State.Checking, is Updates.State.Downloading -> null to {}
                is Updates.State.Available -> "Оновити" to { Updates.download(context, st.info) }
                is Updates.State.ReadyToInstall -> "Встановити" to { Updates.install(context, st.info, st.file) }
                is Updates.State.Failed -> "Ще раз" to {
                    if (st.info != null) Updates.download(context, st.info) else Updates.check(context)
                }
            }
            if (label != null) {
                TextButton(onClick = action) { Text(label, color = Glass.Text, fontWeight = FontWeight.Medium) }
            } else {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), color = Glass.Lavender, strokeWidth = 2.dp)
            }
        }
        if (st is Updates.State.Downloading) {
            LinearProgressIndicator(
                progress = { st.progress },
                color = Glass.Lavender,
                trackColor = Glass.Fill,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.padding(start = 38.dp, end = 8.dp, top = 8.dp).fillMaxWidth().height(4.dp),
            )
        }
        val notes = (st as? Updates.State.Available)?.info?.notes
        if (!notes.isNullOrBlank()) {
            Text(
                notes,
                color = Glass.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 8,
                modifier = Modifier.padding(start = 38.dp, end = 12.dp, top = 8.dp),
            )
        }
    }
}
