package ua.nahadaika.ui

import ua.nahadaika.share.SharedChats
import androidx.compose.material.icons.filled.Group
import android.widget.Toast
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
import ua.nahadaika.ui.theme.ThemeModeButton
import ua.nahadaika.ui.theme.card
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import ua.nahadaika.share.MailInvite
import ua.nahadaika.ui.theme.PrimaryButton
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.InputChip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import ua.nahadaika.share.Contacts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.nahadaika.R
import ua.nahadaika.alarm.AlarmScheduler
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Chat
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.Prefs
import ua.nahadaika.data.alarmAt
import ua.nahadaika.previewText
import ua.nahadaika.shortWhen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    joinCode: String? = null,
    onJoinCodeConsumed: () -> Unit = {},
) {
    val chats by Repo.chats.collectAsStateWithLifecycle(emptyList())
    val reminders by Repo.allReminders.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var deleting by remember { mutableStateOf<Chat?>(null) }

    // Спільні чати: ім'я для учасників, код запрошення, приєднання за кодом.
    val context = LocalContext.current
    val sharing = SharedChats.available()
    var askName by remember { mutableStateOf<(() -> Unit)?>(null) }
    var invite by remember { mutableStateOf<Pair<Chat, String>?>(null) }
    var joining by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    /** Учасники бачать ім'я автора — спершу питаємо його (один раз). */
    fun withName(action: () -> Unit) {
        if (Prefs.displayName(context).isBlank()) askName = action else action()
    }

    fun share(chat: Chat) = withName {
        busy = true
        scope.launch {
            runCatching { SharedChats.share(chat) }
                .onSuccess { invite = chat to it }
                .onFailure { toast(R.string.share_failed) }
            busy = false
        }
    }

    fun join(code: String) = withName {
        busy = true
        scope.launch {
            runCatching { SharedChats.join(code) }
                .onSuccess { id -> if (id != null) onOpenChat(id) else toast(R.string.share_code_not_found) }
                .onFailure { toast(R.string.share_failed) }
            busy = false
        }
    }

    LaunchedEffect(joinCode) {
        if (joinCode != null) {
            if (sharing) joining = joinCode
            onJoinCodeConsumed()
        }
    }

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
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chats_back), onClick = onBack, size = HeaderHeight, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(HeaderHeight).glassHaze(hazeState).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.chats_title), color = Glass.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Search, stringResource(R.string.chats_search), onClick = onOpenSearch, size = HeaderHeight, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                ThemeModeButton(size = HeaderHeight, haze = hazeState)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Default.Settings, stringResource(R.string.chats_settings), onClick = onOpenSettings, size = HeaderHeight, haze = hazeState)
            }
        },
        floatingActionButton = {
            PrimaryCircle(onClick = { creating = true }, size = 58.dp) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.chats_new_chat))
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "mail-invites") {
                    MailInvitesInbox(
                        onAccept = { inv ->
                            withName {
                                busy = true
                                scope.launch {
                                    runCatching { SharedChats.acceptInvite(inv) }
                                        .onSuccess { id -> if (id != null) onOpenChat(id) else toast(R.string.share_code_not_found) }
                                        .onFailure { toast(R.string.share_failed) }
                                    busy = false
                                }
                            }
                        },
                        onDecline = { inv -> scope.launch { SharedChats.declineInvite(inv) } },
                    )
                }
                items(chats, key = { it.id }) { chat ->
                    val pending = reminders.filter { it.chatId == chat.id && !it.fired }.sortedBy { it.alarmAt() }
                    ChatRow(
                        chat = chat,
                        next = pending.firstOrNull(),
                        count = pending.size,
                        onClick = { onOpenChat(chat.id) },
                        onRename = { renaming = chat },
                        onDelete = { deleting = chat },
                        onShare = if (sharing) ({ share(chat) }) else null,
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.chats_new_chat),
            initial = "",
            onDismiss = { creating = false },
            secondary = if (sharing) stringResource(R.string.share_have_code) to { creating = false; joining = "" } else null,
        ) { name ->
            creating = false
            scope.launch { onOpenChat(Repo.createChat(name)) }
        }
    }
    renaming?.let { chat ->
        NameDialog(title = stringResource(R.string.chats_rename), initial = chat.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            scope.launch { Repo.renameChat(chat, name) }
        }
    }
    askName?.let { then ->
        NameDialog(
            title = stringResource(R.string.share_name_title),
            initial = "",
            placeholder = stringResource(R.string.share_name_hint),
            onDismiss = { askName = null },
        ) { name ->
            Prefs.setDisplayName(context, name)
            askName = null
            then()
        }
    }
    invite?.let { (chat, code) ->
        InviteDialog(chat, code, onDismiss = { invite = null })
    }
    joining?.let { initial ->
        JoinDialog(initial, busy = busy, onDismiss = { joining = null }) { code ->
            joining = null
            join(code)
        }
    }
    deleting?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.chats_delete_dialog_title, chat.name)) },
            text = { Text(stringResource(R.string.chats_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { Repo.deleteChat(chat) }
                }) { Text(stringResource(R.string.chats_delete), color = Glass.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.chats_cancel)) } },
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
    onShare: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .card()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(chat, size = 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (SharedChats.isShared(chat)) {
                        Icon(Icons.Default.Group, stringResource(R.string.share_shared_chat), Modifier.size(16.dp), tint = Glass.Lavender)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        chat.name,
                        color = Glass.Text,
                        fontSize = 16.sp,
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
                        text = next?.let { previewText(it).ifBlank { stringResource(R.string.chats_default_reminder_text) } } ?: stringResource(R.string.chats_no_scheduled),
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
            if (onShare != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (SharedChats.isShared(chat)) R.string.share_invite else R.string.share_share)) },
                    onClick = { menu = false; onShare() },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.chats_rename)) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text(stringResource(R.string.chats_delete)) }, onClick = { menu = false; onDelete() })
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
fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    placeholder: String? = null,
    secondary: Pair<String, () -> Unit>? = null,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(placeholder ?: stringResource(R.string.chats_name_dialog_placeholder)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.chats_save)) }
        },
        dismissButton = {
            Row {
                secondary?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.chats_cancel)) }
            }
        },
    )
}

/** Запрошення на гарному фоні: Google-пошта (лист не потрібен — запрошення прийде в застосунок) і посилання-запас. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun InviteDialog(chat: Chat, code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.share_invite_message, chat.name, SharedChats.inviteLink(code), code)
    var email by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(Contacts.list(context)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)
                ?.use { if (it.moveToFirst()) { email = it.getString(0).orEmpty(); invalid = false; sentTo = null } }
        }
    }

    fun invite() {
        if (!Contacts.isEmail(email)) {
            invalid = true
            return
        }
        val to = email.trim().lowercase()
        busy = true
        failed = false
        scope.launch {
            runCatching { SharedChats.inviteByEmail(chat, to) }
                .onSuccess {
                    Contacts.add(context, to)
                    saved = Contacts.list(context)
                    sentTo = to
                    email = ""
                }
                .onFailure { failed = true }
            busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            AppBackground()
            Column(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Close, stringResource(R.string.share_invite_close), tint = Glass.TextDim)
                }
                Text(stringResource(R.string.share_invite_title, chat.name), color = Glass.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; invalid = false; sentTo = null; failed = false },
                        singleLine = true,
                        isError = invalid,
                        label = { Text(stringResource(R.string.share_invite_google_hint)) },
                        supportingText = {
                            Text(
                                when {
                                    invalid -> stringResource(R.string.share_invite_email_invalid)
                                    failed -> stringResource(R.string.share_failed)
                                    sentTo != null -> stringResource(R.string.share_invite_sent, sentTo.orEmpty())
                                    else -> stringResource(R.string.share_invite_google_note)
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { invite() }),
                        trailingIcon = {
                            IconButton(onClick = {
                                picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI))
                            }) { Icon(Icons.Default.Contacts, stringResource(R.string.share_invite_pick_contact)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (saved.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            saved.forEach { c ->
                                InputChip(
                                    selected = c.email == email.trim().lowercase(),
                                    onClick = { email = c.email; invalid = false; sentTo = null },
                                    label = { Text(c.name.ifBlank { c.email }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            stringResource(R.string.share_invite_remove_contact, c.email),
                                            modifier = Modifier.size(16.dp).clickable {
                                                Contacts.remove(context, c.email)
                                                saved = Contacts.list(context)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = stringResource(R.string.share_invite_google_send),
                        onClick = ::invite,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().card().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.share_invite_or_link), color = Glass.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(code, color = Glass.Text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (ua.nahadaika.BuildConfig.FIREBASE_STORAGE_BUCKET.isNotBlank()) R.string.share_invite_note_media else R.string.share_invite_note),
                        color = Glass.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                        context.startActivitySafe(Intent.createChooser(send, null))
                    }) { Text(stringResource(R.string.share_invite_send), color = Glass.Lavender, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Запрошення, що прийшли мені на пошту Google-акаунта: приєднатися чи відхилити. */
@Composable
fun MailInvitesInbox(onAccept: (MailInvite) -> Unit, onDecline: (MailInvite) -> Unit) {
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { SharedChats.refreshInbox() } }
    val invites by SharedChats.inbox.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        invites.forEach { invite ->
            Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
                Text(
                    if (invite.fromName.isBlank()) stringResource(R.string.share_inbox_text_anon, invite.chatName)
                    else stringResource(R.string.share_inbox_text, invite.fromName, invite.chatName),
                    color = Glass.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(stringResource(R.string.share_inbox_accept), onClick = { onAccept(invite) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDecline(invite) }) { Text(stringResource(R.string.share_inbox_decline), color = Glass.TextDim) }
                }
            }
        }
    }
}

/** У чаті: короткий банер про нове запрошення, що веде до списку чатів. */
@Composable
fun MailInviteBanner(onOpenChats: () -> Unit) {
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { SharedChats.refreshInbox() } }
    val first = SharedChats.inbox.collectAsStateWithLifecycle().value.firstOrNull() ?: return
    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Banner(Icons.Default.Group, stringResource(R.string.share_inbox_banner, first.chatName), stringResource(R.string.share_inbox_open), onOpenChats)
    }
}

/** Приєднатися до спільного чату за кодом із запрошення. */
@Composable
private fun JoinDialog(initial: String, busy: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf(initial.uppercase()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_join_title)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.share_join_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = code.length >= 6 && !busy) { Text(stringResource(R.string.share_join_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chats_cancel)) } },
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
                text = stringResource(R.string.chats_banner_notifications_disabled),
                action = stringResource(R.string.chats_banner_enable),
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
                text = stringResource(R.string.chats_banner_exact_alarms_disabled),
                action = stringResource(R.string.chats_banner_allow),
                onAction = {
                    context.startActivitySafe(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }
        UpdateBanner()
        if (!batteryOk) {
            Banner(
                icon = Icons.Default.BatteryAlert,
                text = stringResource(R.string.chats_banner_battery_disabled),
                action = stringResource(R.string.chats_banner_allow),
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

/** Підказка в спільному чаті: хто з учасників ще зі старою версією й не отримає сповіщень та медіа. */
@Composable
fun StaleMembersBanner(remoteId: String?) {
    if (remoteId == null) return
    val context = LocalContext.current
    LaunchedEffect(remoteId) { SharedChats.checkStale(remoteId) }
    val names = SharedChats.stale.collectAsStateWithLifecycle().value[remoteId].orEmpty().sorted()
    if (names.isEmpty()) return
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val key = "stale_hidden_$remoteId"
    val signature = names.joinToString("|")
    // Прихований для цього набору імен; зʼявився новий відстаючий — показуємо знову.
    var hidden by remember(remoteId, signature) { mutableStateOf(prefs.getString(key, null) == signature) }
    if (hidden) return
    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Banner(
            Icons.Default.SystemUpdate,
            stringResource(R.string.share_stale_banner, names.joinToString(", ")),
            stringResource(R.string.share_stale_send),
            onAction = {
                val link = SharedChats.updateLink().orEmpty()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_stale_message, link))
                context.startActivitySafe(Intent.createChooser(send, null))
            },
            onDismiss = {
                prefs.edit().putString(key, signature).apply()
                hidden = true
            },
        )
    }
}

@Composable
internal fun Banner(icon: ImageVector, text: String, action: String, onAction: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).card().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                Icon(Icons.Default.Close, stringResource(R.string.chats_banner_dismiss), tint = Glass.TextFaint, modifier = Modifier.size(16.dp))
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
