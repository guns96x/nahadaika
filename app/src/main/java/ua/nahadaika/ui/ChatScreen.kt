package ua.nahadaika.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.data.alarmAt
import ua.nahadaika.dayLabel
import ua.nahadaika.formatDuration
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.media.MediaFiles
import ua.nahadaika.media.VoiceRecorder
import ua.nahadaika.toLocalDate
import ua.nahadaika.whenLabel
import java.time.LocalDate

private sealed interface ListRow {
    data class Header(val label: String) : ListRow
    data class Msg(val reminder: Reminder) : ListRow
}

private fun buildRows(list: List<Reminder>, timeOf: (Reminder) -> Long): List<ListRow> {
    val rows = mutableListOf<ListRow>()
    var lastDate: LocalDate? = null
    for (r in list) {
        val date = timeOf(r).toLocalDate()
        if (date != lastDate) {
            rows += ListRow.Header(dayLabel(date))
            lastDate = date
        }
        rows += ListRow.Msg(r)
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long, focus: Focus?, onFocusConsumed: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chat by Repo.chat(chatId).collectAsStateWithLifecycle(null)
    val all by Repo.reminders(chatId).collectAsStateWithLifecycle(emptyList())

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scheduled = remember(all) { all.filter { !it.fired }.sortedBy { it.alarmAt() } }
    val history = remember(all) { all.filter { it.fired }.sortedBy { it.lastFiredAt ?: it.triggerAt } }
    val rows = remember(tab, scheduled, history) {
        if (tab == 0) buildRows(scheduled) { it.alarmAt() } else buildRows(history) { it.lastFiredAt ?: it.triggerAt }
    }

    val player = remember { AudioPlayer() }
    val recorder = remember { VoiceRecorder(context) }

    var text by rememberSaveable { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var recording by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var rescheduling by remember { mutableStateOf<Reminder?>(null) }
    var actionsFor by remember { mutableStateOf<Reminder?>(null) }
    var editingText by remember { mutableStateOf<Reminder?>(null) }
    var viewing by remember { mutableStateOf<Reminder?>(null) }
    var highlightId by remember { mutableStateOf<Long?>(null) }

    val currentAttachment by rememberUpdatedState(attachment)
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
            currentAttachment?.file?.delete()
        }
    }
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.tick()
            delay(100)
        }
    }

    // Відкриття зі сповіщення: перемкнути вкладку, підсвітити, за потреби — програти голосове.
    LaunchedEffect(focus, all) {
        val f = focus ?: return@LaunchedEffect
        val r = all.find { it.id == f.reminderId } ?: return@LaunchedEffect
        tab = if (r.fired) 1 else 0
        highlightId = r.id
        if (f.autoplay && r.kind == Kind.VOICE) r.mediaPath?.let(player::play)
        onFocusConsumed()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(tab, highlightId, rows.size) {
        val target = highlightId?.let { id -> rows.indexOfFirst { it is ListRow.Msg && it.reminder.id == id } }?.takeIf { it >= 0 }
            ?: if (tab == 1 && rows.isNotEmpty()) rows.lastIndex else null
        target?.let { listState.animateScrollToItem(it) }
    }
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(2500)
            highlightId = null
        }
    }

    BackHandler(enabled = recording) {
        recorder.cancel()
        recording = false
    }
    BackHandler(enabled = !recording, onBack = onBack)

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun replaceAttachment(new: Attachment?) {
        attachment?.file?.delete()
        attachment = new
    }

    // ---- Камера, галерея, мікрофон ----

    var pendingCapture by remember { mutableStateOf<Attachment?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingCapture
        pendingCapture = null
        if (ok && p != null && p.file.length() > 0) {
            replaceAttachment(p)
        } else {
            p?.file?.delete()
            if (ok) toast("Камера не зберегла фото")
        }
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val p = pendingCapture
        pendingCapture = null
        if (ok && p != null && p.file.length() > 0) {
            replaceAttachment(p.copy(durationMs = MediaFiles.durationOf(p.file)))
        } else {
            p?.file?.delete()
            if (ok) toast("Камера не зберегла відео — спробуйте вибрати його з галереї")
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) { MediaFiles.importFromUri(context, uri) }
                if (imported != null) replaceAttachment(imported) else toast("Не вдалося додати файл")
            }
        }
    }

    fun startRecording() {
        player.stop()
        if (recorder.start()) recording = true else toast("Не вдалося увімкнути мікрофон")
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast("Потрібен доступ до мікрофона для голосових")
    }

    fun capture(kind: Kind) {
        val file = MediaFiles.newFile(context, if (kind == Kind.PHOTO) "jpg" else "mp4")
        val uri = MediaFiles.uriFor(context, file)
        pendingCapture = Attachment(kind, file)
        try {
            if (kind == Kind.PHOTO) takePhoto.launch(uri) else takeVideo.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCapture = null
            file.delete()
            toast("Не знайдено застосунок камери")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                title = {
                    chat?.let { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c, size = 40)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (scheduled.isEmpty()) "немає запланованих" else "заплановано: ${scheduled.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
                attachment?.let { a ->
                    AttachmentPreview(a, player, onRemove = {
                        if (player.currentPath == a.file.absolutePath) player.stop()
                        replaceAttachment(null)
                    })
                }
                if (recording) {
                    RecordingBar(
                        onCancel = {
                            recorder.cancel()
                            recording = false
                        },
                        onDone = {
                            recording = false
                            val voice = recorder.stop()
                            if (voice != null && voice.durationMs >= 700) {
                                replaceAttachment(voice)
                                showSchedule = true
                            } else {
                                voice?.file?.delete()
                                toast("Запис занадто короткий")
                            }
                        },
                    )
                } else {
                    Composer(
                        text = text,
                        onTextChange = { text = it },
                        canSend = text.isNotBlank() || attachment != null,
                        onAttach = { kind ->
                            when (kind) {
                                null -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                else -> capture(kind)
                            }
                        },
                        onMic = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onSend = { showSchedule = true },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Заплановані (${scheduled.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Історія (${history.size})") })
            }
            if (rows.isEmpty()) {
                EmptyState(tab)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(rows, key = { row ->
                        when (row) {
                            is ListRow.Header -> "h-${row.label}"
                            is ListRow.Msg -> row.reminder.id
                        }
                    }) { row ->
                        when (row) {
                            is ListRow.Header -> DateHeader(row.label)
                            is ListRow.Msg -> ReminderBubble(
                                reminder = row.reminder,
                                highlighted = row.reminder.id == highlightId,
                                player = player,
                                onClick = { actionsFor = row.reminder },
                                onOpenMedia = { viewing = row.reminder },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Діалоги ----

    if (showSchedule) {
        ScheduleSheet(
            initialAt = null,
            initialRepeat = Repeat.NONE,
            confirmLabel = "Запланувати",
            onDismiss = { showSchedule = false },
        ) { at, repeat ->
            val a = attachment
            val reminder = Reminder(
                chatId = chatId,
                kind = a?.kind ?: Kind.TEXT,
                text = text.trim(),
                mediaPath = a?.file?.absolutePath,
                durationMs = a?.durationMs ?: 0,
                triggerAt = at,
                repeat = repeat,
            )
            if (a != null && player.currentPath == a.file.absolutePath) player.stop()
            attachment = null
            text = ""
            showSchedule = false
            tab = 0
            scope.launch {
                highlightId = Repo.createReminder(reminder)
                toast("Нагадаю ${whenLabel(at)}")
            }
        }
    }

    rescheduling?.let { r ->
        ScheduleSheet(
            initialAt = if (r.fired) null else r.triggerAt,
            initialRepeat = r.repeat,
            confirmLabel = if (r.fired) "Нагадати ще раз" else "Зберегти",
            onDismiss = { rescheduling = null },
        ) { at, repeat ->
            rescheduling = null
            tab = 0
            scope.launch {
                Repo.reschedule(r, at, repeat)
                highlightId = r.id
                toast("Нагадаю ${whenLabel(at)}")
            }
        }
    }

    actionsFor?.let { r ->
        ReminderActions(
            reminder = r,
            onDismiss = { actionsFor = null },
            onReschedule = { actionsFor = null; rescheduling = r },
            onEditText = { actionsFor = null; editingText = r },
            onSendNow = {
                actionsFor = null
                scope.launch { Repo.deliver(r.id, force = true) }
            },
            onDelete = {
                actionsFor = null
                if (player.currentPath == r.mediaPath) player.stop()
                scope.launch { Repo.deleteReminder(r) }
            },
        )
    }

    editingText?.let { r ->
        var value by remember(r.id) { mutableStateOf(r.text) }
        AlertDialog(
            onDismissRequest = { editingText = null },
            title = { Text(if (r.kind == Kind.TEXT) "Текст нагадування" else "Підпис") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, minLines = 2, maxLines = 8) },
            confirmButton = {
                TextButton(
                    enabled = r.kind != Kind.TEXT || value.isNotBlank(),
                    onClick = {
                        editingText = null
                        scope.launch { Repo.editText(r, value) }
                    },
                ) { Text("Зберегти") }
            },
            dismissButton = { TextButton(onClick = { editingText = null }) { Text("Скасувати") } },
        )
    }

    viewing?.let { MediaViewer(it, onDismiss = { viewing = null }) }
}

@Composable
private fun EmptyState(tab: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (tab == 0) Icons.Default.Schedule else Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (tab == 0) {
                    "Напишіть текст, додайте фото чи відео або запишіть голосове — і оберіть, коли нагадати."
                } else {
                    "Тут з'являться нагадування, які вже надійшли."
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    onAttach: (Kind?) -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.AttachFile, "Додати") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Default.CameraAlt, "Зняти фото") { menu = false; onAttach(Kind.PHOTO) }
                MenuItem(Icons.Default.Videocam, "Зняти відео") { menu = false; onAttach(Kind.VIDEO) }
                MenuItem(Icons.Default.PhotoLibrary, "З галереї") { menu = false; onAttach(null) }
            }
        }
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Нагадування") },
            maxLines = 6,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        if (canSend) {
            FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.AlarmAdd, "Запланувати")
            }
        } else {
            FilledIconButton(onClick = onMic, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Mic, "Записати голосове")
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun RecordingBar(onCancel: () -> Unit, onDone: () -> Unit) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            elapsed = System.currentTimeMillis() - start
            delay(200)
        }
    }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).alpha(pulse).background(Color.Red, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(formatDuration(elapsed), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Скасувати") }
        Spacer(Modifier.width(6.dp))
        FilledIconButton(onClick = onDone, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Check, "Готово") }
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment, player: AudioPlayer, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (attachment.kind) {
                Kind.PHOTO, Kind.VIDEO -> AsyncImage(
                    model = attachment.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                )
                Kind.VOICE -> {
                    val path = attachment.file.absolutePath
                    FilledIconButton(onClick = { player.toggle(path) }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (player.currentPath == path && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Прослухати",
                        )
                    }
                }
                Kind.TEXT -> Unit
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when (attachment.kind) {
                    Kind.PHOTO -> "Фото"
                    Kind.VIDEO -> "Відео · ${formatDuration(attachment.durationMs)}"
                    Kind.VOICE -> "Голосове · ${formatDuration(attachment.durationMs)}"
                    Kind.TEXT -> ""
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Прибрати") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderActions(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onReschedule: () -> Unit,
    onEditText: () -> Unit,
    onSendNow: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                if (reminder.fired) "Надіслано ${whenLabel(reminder.lastFiredAt ?: reminder.triggerAt)}"
                else "Нагадаю ${whenLabel(reminder.alarmAt())}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionItem(Icons.Default.Schedule, if (reminder.fired) "Нагадати ще раз" else "Змінити час", onClick = onReschedule)
            ActionItem(Icons.Default.Edit, if (reminder.kind == Kind.TEXT) "Редагувати текст" else "Змінити підпис", onClick = onEditText)
            if (!reminder.fired) ActionItem(Icons.Default.NotificationsActive, "Надіслати зараз", onClick = onSendNow)
            ActionItem(Icons.Default.Delete, "Видалити", danger = true) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Видалити нагадування?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = tint) },
        leadingContent = { Icon(icon, null, tint = tint) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
