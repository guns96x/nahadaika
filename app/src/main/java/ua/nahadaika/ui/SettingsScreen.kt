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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ua.nahadaika.Prefs
import ua.nahadaika.alarm.AlarmScheduler
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Kind
import ua.nahadaika.media.OfflineSpeech
import ua.nahadaika.media.SpeechPack
import ua.nahadaika.update.UpdateWorker
import ua.nahadaika.update.Updates
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.GlassSegmented
import ua.nahadaika.ui.theme.ThemeMode
import ua.nahadaika.ui.theme.ThemeSettings
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze

/** Налаштування: мовні пакети, розпізнавання, нагадування, запис, вигляд, дозволи. */
@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Налаштування живуть у Prefs; тут — їхні копії для миттєвого перемальовування.
    var autoSchedule by remember { mutableStateOf(Prefs.autoSchedule(context)) }
    var voiceCaption by remember { mutableStateOf(Prefs.voiceCaption(context)) }
    var primary by remember { mutableStateOf(Prefs.speechPrimary(context)) }
    var snooze by remember { mutableIntStateOf(Prefs.snoozeMinutes(context)) }
    var defaultHour by remember { mutableIntStateOf(Prefs.defaultHour(context)) }
    var recordMode by remember { mutableStateOf(Prefs.recordMode(context)) }
    var frontCamera by remember { mutableStateOf(Prefs.frontCamera(context)) }
    var deleting by remember { mutableStateOf<SpeechPack?>(null) }
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdate(context)) }

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
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClick = onBack, size = 56.dp, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(56.dp).glassHaze(hazeState).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Налаштування", color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                item {
                    Section("Розпізнавання голосу") {
                        SwitchRow(
                            Icons.Default.GraphicEq,
                            "Час із голосових і відео",
                            "Сказали «завтра о 9…» — нагадування ставиться саме",
                            autoSchedule,
                        ) {
                            autoSchedule = it
                            Prefs.setAutoSchedule(context, it)
                        }
                        Divider()
                        SwitchRow(
                            Icons.AutoMirrored.Filled.ShortText,
                            "Підпис із розпізнаного",
                            "Додавати сказане текстом до запису",
                            voiceCaption,
                            enabled = autoSchedule,
                        ) {
                            voiceCaption = it
                            Prefs.setVoiceCaption(context, it)
                        }
                    }
                }
                item {
                    Section(
                        "Мовні пакети",
                        footer = "Працюють без інтернету. Потрібні, якщо телефон сам не розпізнає записані голосові.",
                    ) {
                        SpeechPack.entries.forEachIndexed { i, pack ->
                            if (i > 0) Divider()
                            PackRow(pack, onDelete = { deleting = pack })
                        }
                        if (OfflineSpeech.readyPacks.size > 1) {
                            Divider()
                            ChoiceRow(Icons.Default.Translate, "Розпізнавати спершу") {
                                GlassSegmented(
                                    options = SpeechPack.entries.map { null to it.title },
                                    selected = SpeechPack.entries.indexOf(primary),
                                    onSelect = {
                                        primary = SpeechPack.entries[it]
                                        Prefs.setSpeechPrimary(context, primary)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Section("Нагадування") {
                        val snoozeOptions = listOf(5, 10, 15, 30)
                        ChoiceRow(Icons.Default.Snooze, "Відкласти у сповіщенні", "Друга кнопка — завжди «+1 год»") {
                            GlassSegmented(
                                options = snoozeOptions.map { null to "$it хв" },
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
                        ChoiceRow(Icons.Default.Schedule, "Коли, якщо названо лише день", "«Завтра купити хліб» — о котрій нагадати") {
                            GlassSegmented(
                                options = hours.map { null to "$it:00" },
                                selected = hours.indexOf(defaultHour).coerceAtLeast(0),
                                onSelect = {
                                    defaultHour = hours[it]
                                    Prefs.setDefaultHour(context, defaultHour)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        Divider()
                        LinkRow(Icons.AutoMirrored.Filled.VolumeUp, "Звук і вібрація", "Мелодія, вібрація, екран блокування") {
                            context.startActivitySafe(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_ID),
                            )
                        }
                    }
                }
                item {
                    Section("Запис") {
                        ChoiceRow(Icons.Default.Mic, "Кнопка запису за замовчуванням") {
                            GlassSegmented(
                                options = listOf(Icons.Default.Videocam to "Відео", Icons.Default.Mic to "Голосове"),
                                selected = if (recordMode == Kind.VIDEO) 0 else 1,
                                onSelect = {
                                    recordMode = if (it == 0) Kind.VIDEO else Kind.VOICE
                                    Prefs.setRecordMode(context, recordMode)
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                        Divider()
                        ChoiceRow(Icons.Default.CameraFront, "Камера для кружечків") {
                            GlassSegmented(
                                options = listOf(null to "Фронтальна", null to "Основна"),
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
                    Section("Вигляд") {
                        val modes = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO)
                        ChoiceRow(Icons.Default.DarkMode, "Тема") {
                            GlassSegmented(
                                options = listOf(
                                    Icons.Default.DarkMode to "Темна",
                                    Icons.Default.LightMode to "Світла",
                                    Icons.Default.BrightnessAuto to "Авто",
                                ),
                                selected = modes.indexOf(ThemeSettings.mode),
                                onSelect = { ThemeSettings.set(context, modes[it]) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            )
                        }
                    }
                }
                item {
                    Section("Щоб нагадування точно приходили") {
                        StatusRow(Icons.Default.Notifications, "Сповіщення", canNotify) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }
                        Divider()
                        StatusRow(Icons.Default.Timer, "Точні будильники", canExact) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 34) {
                            Divider()
                            StatusRow(Icons.Default.Alarm, "Будильник на весь екран", canFullScreen) {
                                context.startActivitySafe(
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }
                        Divider()
                        StatusRow(Icons.Default.BatteryAlert, "Робота у фоні без обмежень", batteryOk) {
                            context.startActivitySafe(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                }
                item {
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
                item {
                    Section("Про застосунок") {
                        val version = remember {
                            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
                        }
                        InfoRow(
                            Icons.Default.Info,
                            "Нагадайка $version",
                            "Розпізнавання мовлення — Vosk (Apache 2.0). Шрифт — Inter (SIL OFL).",
                        )
                    }
                }
            }
        }
    }

    deleting?.let { pack ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити «${pack.title}»?") },
            text = { Text("Звільниться ≈${OfflineSpeech.sizeMb(pack)} МБ. Пакет можна буде завантажити знову.") },
            confirmButton = {
                TextButton(onClick = {
                    OfflineSpeech.delete(pack)
                    deleting = null
                }) { Text("Видалити", color = Glass.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

// ---- Складові ----

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title.uppercase(),
            color = Glass.TextFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)), content = content)
        if (footer != null) {
            Text(footer, color = Glass.TextFaint, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 54.dp).fillMaxWidth().height(0.6.dp).background(Glass.FillStrong))
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = Glass.TextDim) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
}

@Composable
private fun Titles(title: String, subtitle: String?, modifier: Modifier = Modifier, dim: Boolean = false) {
    Column(modifier) {
        Text(title, color = if (dim) Glass.TextFaint else Glass.Text, fontSize = 16.sp)
        if (subtitle != null) {
            Text(subtitle, color = Glass.TextFaint, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
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
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Spacer(Modifier.width(16.dp))
            Titles(title, subtitle, Modifier.weight(1f))
        }
        Box(Modifier.padding(start = 38.dp, top = 10.dp)) { control() }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(16.dp))
        Titles(title, subtitle, Modifier.weight(1f))
    }
}

/** Дозвіл: ✓ якщо надано, інакше кнопка «Дозволити». */
@Composable
private fun StatusRow(icon: ImageVector, title: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onFix).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (ok) Glass.TextDim else Glass.Danger)
        Spacer(Modifier.width(16.dp))
        Text(title, color = Glass.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (ok) {
            Icon(Icons.Default.Check, "Увімкнено", tint = Glass.Lavender, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        } else {
            TextButton(onClick = onFix) { Text("Дозволити", color = Glass.Text, fontWeight = FontWeight.Medium) }
        }
    }
}

/** Мовний пакет: розмір, хід завантаження, «Завантажити / Скасувати / Видалити». */
@Composable
private fun PackRow(pack: SpeechPack, onDelete: () -> Unit) {
    val st = OfflineSpeech.state(pack)
    val subtitle = when (st) {
        OfflineSpeech.State.Missing -> "${pack.downloadMb} МБ, займе ${pack.diskMb} МБ"
        is OfflineSpeech.State.Downloading -> "Завантаження… ${(st.progress * 100).toInt()}%"
        OfflineSpeech.State.Ready -> "Встановлено · ${remember(st) { OfflineSpeech.sizeMb(pack) }} МБ"
        is OfflineSpeech.State.Failed -> "Не вдалося: ${st.message}"
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(
                if (st == OfflineSpeech.State.Ready) Icons.Default.Check else Icons.Default.Translate,
                if (st == OfflineSpeech.State.Ready) Glass.Lavender else Glass.TextDim,
            )
            Spacer(Modifier.width(16.dp))
            Titles(pack.title, subtitle, Modifier.weight(1f))
            val (label, action) = when (st) {
                OfflineSpeech.State.Missing -> "Завантажити" to { OfflineSpeech.download(pack) }
                is OfflineSpeech.State.Downloading -> "Скасувати" to { OfflineSpeech.cancelDownload(pack) }
                OfflineSpeech.State.Ready -> "Видалити" to onDelete
                is OfflineSpeech.State.Failed -> "Ще раз" to { OfflineSpeech.download(pack) }
            }
            TextButton(onClick = action) {
                Text(label, color = if (st == OfflineSpeech.State.Ready) Glass.Danger else Glass.Text, fontWeight = FontWeight.Medium)
            }
        }
        if (st is OfflineSpeech.State.Downloading) {
            LinearProgressIndicator(
                progress = { st.progress },
                color = Glass.Lavender,
                trackColor = Glass.Fill,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.padding(start = 38.dp, end = 8.dp, top = 8.dp).fillMaxWidth().height(4.dp),
            )
        }
    }
}

private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576f).replace('.', ',') + " МБ"

/** Поточна версія, кнопка «Перевірити оновлення» і хід оновлення. */
@Composable
fun UpdateRow() {
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
                Updates.State.Idle, Updates.State.UpToDate -> "Перевірити" to { Updates.check(context) }
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
