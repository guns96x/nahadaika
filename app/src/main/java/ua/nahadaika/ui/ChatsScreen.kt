package ua.nahadaika.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import ua.nahadaika.data.alarmAt
import ua.nahadaika.previewText
import ua.nahadaika.shortWhen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(onOpenChat: (Long) -> Unit) {
    val chats by Repo.chats.collectAsStateWithLifecycle(emptyList())
    val reminders by Repo.allReminders.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var deleting by remember { mutableStateOf<Chat?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Нагадайка", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новий чат")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item { PermissionBanners() }
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
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
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
                }) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
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
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(chat, size = 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (next != null) {
                        if (next.repeat != Repeat.NONE) {
                            Icon(Icons.Default.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            shortWhen(next.alarmAt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = next?.let { previewText(it).ifBlank { "Нагадування" } } ?: "Немає запланованих",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$count", modifier = Modifier.padding(horizontal = 2.dp))
                        }
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
    Box(
        modifier = Modifier.size(size.dp).background(Color(chat.color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            chat.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = (size * 0.42).sp,
            fontWeight = FontWeight.SemiBold,
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
private fun PermissionBanners() {
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
        if (!canNotify) {
            Banner(
                icon = Icons.Default.NotificationsOff,
                text = "Сповіщення вимкнені — нагадування не з'являтимуться.",
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
                text = "Дозвольте точні будильники, щоб нагадування приходили хвилина в хвилину.",
                action = "Дозволити",
                onAction = {
                    context.startActivitySafe(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }
        if (!batteryOk) {
            Banner(
                icon = Icons.Default.BatteryAlert,
                text = "Деякі телефони (Xiaomi, Samsung, Huawei) «присипляють» застосунки. Дозвольте роботу у фоні.",
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

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onAction: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Пізніше") }
            TextButton(onClick = onAction) { Text(action) }
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
