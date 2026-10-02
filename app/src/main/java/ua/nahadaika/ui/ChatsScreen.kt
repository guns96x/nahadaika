package ua.nahadaika.ui

import android.Manifest
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.lerp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ua.nahadaika.ui.theme.PrimaryCircle
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.nahadaika.alarm.AlarmScheduler
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Chat
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.Prefs
import ua.nahadaika.media.OfflineSpeech
import ua.nahadaika.update.Updates
import ua.nahadaika.media.SpeechPack
import ua.nahadaika.data.alarmAt
import ua.nahadaika.previewText
import ua.nahadaika.shortWhen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onOpenChat: (Long) -> Unit, onBack: () -> Unit, onOpenSettings: () -> Unit = {}, onOpenSearch: () -> Unit = {}) {
    val chats by Repo.chats.collectAsStateWithLifecycle(emptyList())
    val reminders by Repo.allReminders.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var deleting by remember { mutableStateOf<Chat?>(null) }

    BackHandler(onBack = onBack)

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
                    Text("Чати", color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Search, "Пошук", onClick = onOpenSearch, size = 56.dp, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Settings, "Налаштування", onClick = onOpenSettings, size = 56.dp, haze = hazeState)
            }
        },
        floatingActionButton = {
            PrimaryCircle(onClick = { creating = true }, size = 58.dp) {
                Icon(Icons.Default.Add, contentDescription = "Новий чат")
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
                    top = padding.calculateTopPadding() + 6.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    val pending = reminders.filter { it.chatId == chat.id && !it.fired }.sortedBy { it.alarmAt() }
                    ChatRow(
                        chat = chat,
                        next = pending.firstOrNull(),
                        count = pending.size,
                        onClick = { onOpenChat(chat.id) },
                        onRename = { renaming = chat },
                        onDelete = { deleting = chat },
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog(title = "Новий чат", initial = "", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { onOpenChat(Repo.createChat(name)) }
        }
    }
    renaming?.let { chat ->
        NameDialog(title = "Перейменувати", initial = chat.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch { Repo.renameChat(chat, name) }
        }
    }
    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Видалити «${chat.name}»?") },
            text = { Text("Усі нагадування в цьому чаті буде видалено.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { Repo.deleteChat(chat) }
                }) { Text("Видалити", color = Glass.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Скасувати") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: Chat,
    next: Reminder?,
    count: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(chat, size = 54)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.name,
                        color = Glass.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (next != null) {
                        if (next.repeat != Repeat.NONE) {
                            Icon(Icons.Default.Repeat, null, Modifier.size(14.dp), tint = Glass.TextDim)
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(shortWhen(next.alarmAt()), fontSize = 13.sp, color = Glass.TextDim)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = next?.let { previewText(it).ifBlank { "Нагадування" } } ?: "Немає запланованих",
                        fontSize = 14.sp,
                        color = Glass.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        Box(
                            Modifier.padding(start = 8.dp).clip(Glass.Pill).background(Glass.FillStrong).padding(horizontal = 9.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("$count", color = Glass.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Перейменувати") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Видалити") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
fun Avatar(chat: Chat, size: Int) {
    val tone = Color(chat.color)
    Box(
        modifier = Modifier
            .size(size.dp)
            .glass(CircleShape, tone.copy(alpha = if (Glass.palette.isDark) 0.20f else 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            chat.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = if (Glass.palette.isDark) lerp(tone, Color.White, 0.45f) else lerp(tone, Color.Black, 0.25f),
            fontSize = (size * 0.40).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Наприклад: Дім, Робота, Ліки") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

// ---- Дозволи, без яких нагадування можуть не прийти ----

private const val PREFS = "ui"
private const val KEY_BATTERY_DISMISSED = "battery_dismissed"

@SuppressLint("BatteryLife")
@Composable
fun PermissionBanners() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.openAppNotificationSettings()
        refresh++
    }

    // Одноразово просимо дозвіл на сповіщення при першому запуску.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // Стан дозволів перечитуємо після повернення з налаштувань (refresh змінюється в ON_RESUME).
    val canNotify = remember(refresh) { Notifier.canNotify(context) }
    val canExact = remember(refresh) { AlarmScheduler.canScheduleExact(context) }
    val batteryOk = remember(refresh) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) ||
            prefs.getBoolean(KEY_BATTERY_DISMISSED, false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (!canNotify) {
            Banner(
                icon = Icons.Default.NotificationsOff,
                text = "Сповіщення вимкнені",
                action = "Увімкнути",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openAppNotificationSettings()
                    }
                },
            )
        }
        if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Banner(
                icon = Icons.Default.Timer,
                text = "Точні будильники вимкнені",
                action = "Дозволити",
                onAction = {
                    context.startActivitySafe(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }
        UpdateBanner()
        SpeechBanner()
        if (!batteryOk) {
            Banner(
                icon = Icons.Default.BatteryAlert,
                text = "Робота у фоні вимкнена",
                action = "Дозволити",
                onAction = {
                    context.startActivitySafe(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
                onDismiss = {
                    prefs.edit().putBoolean(KEY_BATTERY_DISMISSED, true).apply()
                    refresh++
                },
            )
        }
    }
}

/** Нова версія застосунку: «Оновити» одним натиском, далі — хід завантаження. */
@Composable
private fun UpdateBanner() {
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

/** Офлайн-розпізнавання голосу: хід завантаження або пропозиція завантажити. */
@Composable
private fun SpeechBanner() {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(Prefs.speechBannerDismissed(context)) }
    when (val st = OfflineSpeech.state) {
        is OfflineSpeech.State.Downloading -> Banner(
            icon = Icons.Default.Download,
            text = "Завантажую розпізнавання голосу… ${(st.progress * 100).toInt()}%",
            action = "Скасувати",
            onAction = OfflineSpeech::cancelDownload,
        )
        is OfflineSpeech.State.Failed -> Banner(
            icon = Icons.Default.Download,
            text = "Не вдалося завантажити розпізнавання: ${st.message}",
            action = "Ще раз",
            onAction = OfflineSpeech::download,
        )
        OfflineSpeech.State.Missing -> if (Prefs.speechOfferShown(context) && !dismissed) {
            Banner(
                icon = Icons.Default.GraphicEq,
                text = "Розпізнавати час у голосових (≈${SpeechPack.UK.downloadMb} МБ)",
                action = "Завантажити",
                onAction = OfflineSpeech::download,
                onDismiss = {
                    Prefs.setSpeechBannerDismissed(context)
                    dismissed = true
                },
            )
        }
        OfflineSpeech.State.Ready -> Unit
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onAction: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).glass(RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Glass.Lavender, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Glass.TextDim, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(action, color = Glass.Text, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Пізніше", tint = Glass.TextFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun Context.openAppNotificationSettings() {
    startActivitySafe(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

fun Context.startActivitySafe(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
