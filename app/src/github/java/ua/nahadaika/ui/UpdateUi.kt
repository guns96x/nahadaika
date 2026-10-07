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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.nahadaika.Prefs
import ua.nahadaika.R
import ua.nahadaika.Res
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
            text = stringResource(R.string.upd_banner_available, st.info.versionName),
            action = stringResource(R.string.upd_action_update),
            onAction = { Updates.download(context, st.info) },
        )
        is Updates.State.Downloading -> Banner(
            icon = Icons.Default.SystemUpdate,
            text = stringResource(R.string.upd_banner_downloading, (st.progress * 100).toInt()),
            action = "",
            onAction = {},
        )
        is Updates.State.ReadyToInstall -> Banner(
            icon = Icons.Default.SystemUpdate,
            text = stringResource(R.string.upd_banner_ready, st.info.versionName),
            action = stringResource(R.string.upd_action_install),
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
    Section(stringResource(R.string.upd_section_title)) {
        UpdateRow()
        Divider()
        SwitchRow(
            Icons.Default.Autorenew,
            stringResource(R.string.upd_auto_check_title),
            stringResource(R.string.upd_auto_check_subtitle),
            autoUpdate,
        ) {
            autoUpdate = it
            Prefs.setAutoUpdate(context, it)
            UpdateWorker.schedule(context, it)
        }
    }
}

private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576f).replace('.', ',') + " " + Res.s(R.string.upd_unit_mb)

/** Поточна версія, кнопка «Перевірити оновлення» і хід оновлення. */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val current = remember { Updates.currentVersionName(context) }
    val st = Updates.state
    val currentVersion = stringResource(R.string.upd_version_current, current)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Default.SystemUpdate, if (st is Updates.State.Available) Glass.Lavender else Glass.TextDim)
            Spacer(Modifier.width(16.dp))
            val (title, subtitle) = when (st) {
                Updates.State.Idle -> currentVersion to stringResource(R.string.upd_subtitle_idle)
                Updates.State.Checking -> currentVersion to stringResource(R.string.upd_subtitle_checking)
                Updates.State.UpToDate -> currentVersion to stringResource(R.string.upd_subtitle_up_to_date)
                Updates.State.NoReleases -> currentVersion to stringResource(R.string.upd_subtitle_no_releases)
                is Updates.State.Available -> stringResource(R.string.upd_available_version, st.info.versionName) to
                    if (st.info.patch != null) stringResource(R.string.upd_download_patch_vs_apk, mb(st.info.downloadSize), mb(st.info.apk.size))
                    else stringResource(R.string.upd_download_apk, mb(st.info.apk.size))
                is Updates.State.Downloading -> stringResource(R.string.upd_downloading_to, st.info.versionName) to
                    stringResource(
                        R.string.upd_downloading_progress,
                        if (st.patch) stringResource(R.string.upd_patch) else stringResource(R.string.upd_full_apk),
                        (st.progress * 100).toInt(),
                    )
                is Updates.State.ReadyToInstall -> stringResource(R.string.upd_ready_title, st.info.versionName) to
                    stringResource(R.string.upd_ready_subtitle)
                is Updates.State.Failed -> currentVersion to st.message
            }
            Titles(title, subtitle, Modifier.weight(1f))
            val (label, action) = when (st) {
                Updates.State.Idle, Updates.State.UpToDate, Updates.State.NoReleases -> stringResource(R.string.upd_action_check) to { Updates.check(context) }
                Updates.State.Checking, is Updates.State.Downloading -> null to {}
                is Updates.State.Available -> stringResource(R.string.upd_action_update) to { Updates.download(context, st.info) }
                is Updates.State.ReadyToInstall -> stringResource(R.string.upd_action_install) to { Updates.install(context, st.info, st.file) }
                is Updates.State.Failed -> stringResource(R.string.upd_action_retry) to {
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
