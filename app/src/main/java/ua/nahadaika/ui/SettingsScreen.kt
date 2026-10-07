package ua.nahadaika.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ua.nahadaika.Prefs
import ua.nahadaika.voice.SmartVoice
import ua.nahadaika.R
import ua.nahadaika.Res
import ua.nahadaika.alarm.AlarmScheduler
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Kind
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.card
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.GlassSegmented
import ua.nahadaika.ui.theme.ThemeMode
import ua.nahadaika.ui.theme.ThemeSettings
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import ua.nahadaika.data.Repo
import ua.nahadaika.data.BackupFormatException
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate

/** Налаштування: нагадування, запис, вигляд, дозволи. */
@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Налаштування живуть у Prefs; тут — їхні копії для миттєвого перемальовування.
    var smartVoice by remember { mutableStateOf(Prefs.smartVoice(context) == true) }
    var snooze by remember { mutableIntStateOf(Prefs.snoozeMinutes(context)) }
    var defaultHour by remember { mutableIntStateOf(Prefs.defaultHour(context)) }
    var recordMode by remember { mutableStateOf(Prefs.recordMode(context)) }
    var frontCamera by remember { mutableStateOf(Prefs.frontCamera(context)) }

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val canNotify = remember(refresh) { Notifier.canNotify(context) }
    val canExact = remember(refresh) { AlarmScheduler.canScheduleExact(context) }
    val canFullScreen = remember(refresh) { Notifier.canFullScreen(context) }
    val batteryOk = remember(refresh) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val hazeState = remember { HazeState() }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .edgeFade(top = true)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back), onClick = onBack, size = HeaderHeight, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(HeaderHeight).glassHaze(hazeState).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.settings_title), color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            AppBackground()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (SmartVoice.available()) {
                    item {
                        Section(stringResource(R.string.settings_section_smart)) {
                            SwitchRow(
                                Icons.Default.GraphicEq,
                                stringResource(R.string.settings_smart_title),
                                stringResource(R.string.settings_smart_subtitle),
                                smartVoice,
                            ) {
                                smartVoice = it
                                Prefs.setSmartVoice(context, it)
                            }
                        }
                    }
                }
                item {
                    Section(stringResource(R.string.settings_section_reminders)) {
                        val snoozeOptions = listOf(5, 10, 15, 30)
                        ChoiceRow(Icons.Default.Snooze, stringResource(R.string.settings_snooze_title), stringResource(R.string.settings_snooze_subtitle)) {
                            GlassSegmented(
                                options = snoozeOptions.map { null to stringResource(R.string.settings_snooze_min, it) },
                                selected = snoozeOptions.indexOf(snooze).coerceAtLeast(0),
                                onSelect = {
                                    snooze = snoozeOptions[it]
                                    Prefs.setSnoozeMinutes(context, snooze)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        Divider()
                        val hours = listOf(7, 8, 9, 10, 12)
                        ChoiceRow(Icons.Default.Schedule, stringResource(R.string.settings_default_time_title), stringResource(R.string.settings_default_time_subtitle)) {
                            GlassSegmented(
                                options = hours.map { null to stringResource(R.string.settings_hour_format, it) },
                                selected = hours.indexOf(defaultHour).coerceAtLeast(0),
                                onSelect = {
                                    defaultHour = hours[it]
                                    Prefs.setDefaultHour(context, defaultHour)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        Divider()
                        LinkRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.settings_sound_title), stringResource(R.string.settings_sound_subtitle)) {
                            context.startActivitySafe(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_ID),
                            )
                        }
                    }
                }
                item {
                    Section(stringResource(R.string.settings_section_record)) {
                        ChoiceRow(Icons.Default.Mic, stringResource(R.string.settings_default_record_button)) {
                            GlassSegmented(
                                options = listOf(Icons.Default.Videocam to stringResource(R.string.settings_record_video), Icons.Default.Mic to stringResource(R.string.settings_record_voice)),
                                selected = if (recordMode == Kind.VIDEO) 0 else 1,
                                onSelect = {
                                    recordMode = if (it == 0) Kind.VIDEO else Kind.VOICE
                                    Prefs.setRecordMode(context, recordMode)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        Divider()
                        ChoiceRow(Icons.Default.CameraFront, stringResource(R.string.settings_camera_title)) {
                            GlassSegmented(
                                options = listOf(null to stringResource(R.string.settings_camera_front), null to stringResource(R.string.settings_camera_back)),
                                selected = if (frontCamera) 0 else 1,
                                onSelect = {
                                    frontCamera = it == 0
                                    Prefs.setFrontCamera(context, frontCamera)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                    }
                }
                item {
                    Section(stringResource(R.string.settings_section_appearance)) {
                        val modes = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO)
                        ChoiceRow(Icons.Default.DarkMode, stringResource(R.string.settings_theme_title)) {
                            GlassSegmented(
                                options = listOf(
                                    Icons.Default.DarkMode to stringResource(R.string.settings_theme_dark),
                                    Icons.Default.LightMode to stringResource(R.string.settings_theme_light),
                                    Icons.Default.BrightnessAuto to stringResource(R.string.settings_theme_auto),
                                ),
                                selected = modes.indexOf(ThemeSettings.mode),
                                onSelect = { ThemeSettings.set(context, modes[it]) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        // Мова застосунку окремо від системної — лише з Android 13.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Divider()
                            LinkRow(Icons.Default.Translate, stringResource(R.string.settings_language_title), stringResource(R.string.settings_language_subtitle)) {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                    }
                }
                item {
                    Section(stringResource(R.string.settings_section_permissions)) {
                        StatusRow(Icons.Default.Notifications, stringResource(R.string.settings_perm_notifications), canNotify) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                            }
                        }
                        Divider()
                        StatusRow(Icons.Default.Timer, stringResource(R.string.settings_perm_exact_alarms), canExact) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 34) {
                            Divider()
                            StatusRow(Icons.Default.Alarm, stringResource(R.string.settings_perm_fullscreen), canFullScreen) {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                        Divider()
                        StatusRow(Icons.Default.BatteryAlert, stringResource(R.string.settings_perm_battery), batteryOk) {
                            context.startActivitySafe(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                }
                item { BackupSection() }
                item { UpdateSection() }
                item {
                    Section(stringResource(R.string.settings_section_about)) {
                        val version = remember {
                            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                        }
                        InfoRow(
                            Icons.Default.Info,
                            stringResource(R.string.settings_about_app_name, version),
                            stringResource(R.string.settings_about_font_credit),
                        )
                    }
                }
            }
        }
    }
}

// ---- Складові ----

@Composable
internal fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title,
            color = Glass.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 10.dp),
        )
        Column(Modifier.fillMaxWidth().card(), content = content)
        if (footer != null) {
            Text(footer, color = Glass.TextFaint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

@Composable
internal fun Divider() {
    Box(Modifier.padding(start = 48.dp).fillMaxWidth().height(0.6.dp).background(Glass.FillStrong))
}

@Composable
internal fun RowIcon(icon: ImageVector, tint: Color = Glass.TextDim) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
internal fun Titles(title: String, subtitle: String?, modifier: Modifier = Modifier, dim: Boolean = false) {
    Column(modifier) {
        Text(title, color = if (dim) Glass.TextFaint else Glass.Text, fontSize = 15.sp)
        if (subtitle != null) {
            Text(subtitle, color = Glass.TextFaint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Titles(title, subtitle, Modifier.weight(1f), dim = !enabled)
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Glass.OnPrimary,
                checkedTrackColor = Glass.Primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Glass.TextDim,
                uncheckedTrackColor = Glass.Fill,
                uncheckedBorderColor = Glass.FillStrong,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String? = null, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Spacer(Modifier.width(16.dp))
            Titles(title, subtitle, Modifier.weight(1f))
        }
        Box(Modifier.padding(start = 34.dp, top = 6.dp)) { control() }
    }
}

/** Резервна копія в один ZIP-файл: зберегти куди завгодно (Диск, пам'ять) і відновити на новому телефоні. */
@Composable
private fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { Repo.exportBackup(it) } }.isSuccess
            }
            busy = false
            toast(if (ok) Res.s(R.string.settings_backup_saved) else Res.s(R.string.settings_backup_save_failed))
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { Repo.importBackup(it) } }
            }
            busy = false
            result.onSuccess {
                val msg = if (it.reminders == 0 && it.chats == 0) {
                    Res.s(R.string.settings_backup_empty)
                } else if (it.skipped > 0) {
                    Res.s(R.string.settings_backup_restored_with_skipped, it.reminders, it.chats, it.skipped)
                } else {
                    Res.s(R.string.settings_backup_restored, it.reminders, it.chats)
                }
                toast(msg)
            }.onFailure { toast((it as? BackupFormatException)?.message ?: Res.s(R.string.settings_backup_restore_failed)) }
        }
    }

    Section(
        stringResource(R.string.settings_section_backup),
        footer = stringResource(R.string.settings_backup_footer),
    ) {
        LinkRow(Icons.Default.Backup, stringResource(R.string.settings_backup_save_title), if (busy) stringResource(R.string.settings_backup_busy) else stringResource(R.string.settings_backup_save_subtitle)) {
            if (!busy) save.launch("nahadaika-${LocalDate.now()}.zip")
        }
        Divider()
        LinkRow(Icons.Default.Restore, stringResource(R.string.settings_backup_restore_title), stringResource(R.string.settings_backup_restore_subtitle)) {
            if (!busy) restore.launch(arrayOf("application/zip", "application/octet-stream"))
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Titles(title, subtitle, Modifier.weight(1f))
        Text("›", color = Glass.TextFaint, fontSize = 22.sp)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Titles(title, subtitle, Modifier.weight(1f))
    }
}

/** Дозвіл: ✓ якщо надано, інакше кнопка «Дозволити». */
@Composable
private fun StatusRow(icon: ImageVector, title: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onFix).padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (ok) Glass.TextDim else Glass.Danger)
        Spacer(Modifier.width(16.dp))
        Text(title, color = Glass.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (ok) {
            Icon(Icons.Default.Check, stringResource(R.string.settings_perm_enabled_desc), tint = Glass.Lavender, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        } else {
            TextButton(onClick = onFix) { Text(stringResource(R.string.settings_perm_allow), color = Glass.Text, fontWeight = FontWeight.Medium) }
        }
    }
}
