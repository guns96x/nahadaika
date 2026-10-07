package ua.nahadaika.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.nahadaika.R
import ua.nahadaika.Res
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.nahadaika.Occurrence
import ua.nahadaika.Prefs
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.data.alarmAt
import ua.nahadaika.dayLabel
import ua.nahadaika.inLabel
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.media.MediaFiles
import ua.nahadaika.media.VoiceRecorder
import ua.nahadaika.occurrencesOn
import ua.nahadaika.previewText
import ua.nahadaika.soonLabel
import ua.nahadaika.toLocalDate
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.GlassSnackbar
import ua.nahadaika.ui.theme.ThemeModeButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glassHaze
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/** Рядок таймлайну: нагадування або лінія «зараз». */
private sealed interface Entry {
    data class Item(val occurrence: Occurrence) : Entry
    data object Now : Entry
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: Long,
    focus: Focus?,
    onFocusConsumed: () -> Unit,
    quick: Quick?,
    onQuickConsumed: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chat by Repo.chat(chatId).collectAsStateWithLifecycle(null)
    val all by Repo.reminders(chatId).collectAsStateWithLifecycle(emptyList())
    val statList by Repo.commentStats(chatId).collectAsStateWithLifecycle(emptyList())
    val stats = remember(statList) { statList.associateBy { it.reminderId } }
    // Розгорнута картка — одна; тап по іншій згортає попередню.
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val scheduled = remember(all) { all.filter { !it.fired }.sortedBy { it.alarmAt() } }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick = System.currentTimeMillis()
        }
    }
    // Таймлайн обраного дня (як у Structured): нагадування по часу + лінія «зараз» сьогодні.
    val today = remember(nowTick) { nowTick.toLocalDate() }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val entries = remember(all, selectedDate, nowTick) {
        val items = occurrencesOn(all, selectedDate).map { Entry.Item(it) }
        if (selectedDate != today) {
            items
        } else {
            val split = items.indexOfFirst { it.occurrence.at > nowTick }.let { if (it < 0) items.size else it }
            items.take(split) + Entry.Now + items.drop(split)
        }
    }

    val player = remember { AudioPlayer() }
    val recorder = remember { VoiceRecorder(context) }

    var text by rememberSaveable { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var recordMode by remember { mutableStateOf(Prefs.recordMode(context)) }
    // Поточний запис (голосове чи відео), зсув пальця під час утримання, стан відео-«кружечка».
    var rec by remember { mutableStateOf<Rec?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var videoFinish by remember { mutableStateOf<Boolean?>(null) }
    var frontCamera by remember { mutableStateOf(Prefs.frontCamera(context)) }
    var pendingStart by remember { mutableStateOf<Pair<Kind, Boolean>?>(null) }
    var showSchedule by remember { mutableStateOf(false) }
    // Нове нагадування — як будильник (гучно, на весь екран).
    var composeAlarm by remember { mutableStateOf(false) }
    var rescheduling by remember { mutableStateOf<Reminder?>(null) }
    var actionsFor by remember { mutableStateOf<Reminder?>(null) }
    var editingText by remember { mutableStateOf<Reminder?>(null) }
    var viewing by remember { mutableStateOf<Reminder?>(null) }
    // Відео грає прямо в бульбашці; одночасно — одне відео або одне голосове.
    var playingVideoId by remember { mutableStateOf<Long?>(null) }
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
        if (player.isPlaying) playingVideoId = null
        while (player.isPlaying) {
            player.tick()
            delay(100)
        }
    }

    // Відкриття зі сповіщення: показати день нагадування, підсвітити, за потреби — програти голосове.
    LaunchedEffect(focus, all) {
        val f = focus ?: return@LaunchedEffect
        val r = all.find { it.id == f.reminderId } ?: return@LaunchedEffect
        selectedDate = (if (r.fired) r.lastFiredAt ?: r.triggerAt else r.alarmAt()).toLocalDate()
        highlightId = r.id
        expandedId = r.id
        if (f.autoplay && r.kind == Kind.VOICE) r.mediaPath?.let(player::play)
        onFocusConsumed()
    }

    val listState = rememberLazyListState()
    // Перед таймлайном у списку — рядок банерів.
    LaunchedEffect(selectedDate, highlightId, entries.size) {
        val target = highlightId?.let { id -> entries.indexOfFirst { it is Entry.Item && it.occurrence.reminder.id == id } }
            ?.takeIf { it >= 0 }
            ?: entries.indexOf(Entry.Now).takeIf { it > 1 }?.minus(1)
        target?.let { listState.animateScrollToItem(it + 1) }
    }
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(2500)
            highlightId = null
        }
    }


    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun replaceAttachment(new: Attachment?) {
        attachment?.file?.delete()
        attachment = new
    }

    /** Створити нагадування й показати підтвердження з кнопкою «Змінити». */
    fun schedule(reminder: Reminder) {
        selectedDate = reminder.triggerAt.toLocalDate()
        scope.launch {
            val id = Repo.createReminder(reminder)
            highlightId = id
            val label = previewText(reminder).take(40)
            val prefix = if (reminder.alarm) Res.s(R.string.chat_snackbar_alarm, soonLabel(reminder.triggerAt))
            else Res.s(R.string.chat_snackbar_reminder, soonLabel(reminder.triggerAt))
            val result = snackbar.showSnackbar(
                message = prefix +
                    if (label.isNotBlank()) ": $label" else "",
                actionLabel = Res.s(R.string.chat_action_change),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) rescheduling = reminder.copy(id = id)
        }
    }

    fun scheduleComposed(at: Long, repeat: Repeat, textOverride: String? = null, alarm: Boolean = composeAlarm) {
        val a = attachment
        val body = (textOverride ?: text).trim()
        val reminder = Reminder(
            chatId = chatId,
            kind = a?.kind ?: Kind.TEXT,
            text = if (a == null) body.ifEmpty { Res.s(R.string.chat_default_reminder_text) } else body,
            mediaPath = a?.file?.absolutePath,
            durationMs = a?.durationMs ?: 0,
            triggerAt = at,
            repeat = repeat,
            alarm = alarm,
        )
        composeAlarm = false
        if (a != null && player.currentPath == a.file.absolutePath) player.stop()
        attachment = null
        text = ""
        showSchedule = false
        schedule(reminder)
    }

    /** Прикріпити запис і одразу відкрити вибір часу. */
    fun attachRecording(a: Attachment) {
        replaceAttachment(a)
        showSchedule = true
    }

    // ---- Камера, галерея, мікрофон ----

    var pendingPhoto by remember { mutableStateOf<Attachment?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPhoto
        pendingPhoto = null
        if (ok && p != null && p.file.length() > 0) {
            replaceAttachment(p)
        } else {
            p?.file?.delete()
            if (ok) toast(Res.s(R.string.chat_camera_failed_save))
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) { MediaFiles.importFromUri(context, uri) }
                if (imported != null) replaceAttachment(imported) else toast(Res.s(R.string.chat_error_add_file))
            }
        }
    }

    fun launchPhoto() {
        val file = MediaFiles.newFile(context, "jpg")
        pendingPhoto = Attachment(Kind.PHOTO, file)
        try {
            takePhoto.launch(MediaFiles.uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            file.delete()
            toast(Res.s(R.string.chat_camera_app_not_found))
        }
    }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPhoto() else toast(Res.s(R.string.chat_permission_camera_needed))
    }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasRecordPermissions(kind: Kind) =
        granted(Manifest.permission.RECORD_AUDIO) && (kind != Kind.VIDEO || granted(Manifest.permission.CAMERA))

    fun beginRecording(kind: Kind, locked: Boolean): Boolean {
        player.stop()
        playingVideoId = null
        if (kind == Kind.VOICE && !recorder.start()) {
            toast(Res.s(R.string.chat_microphone_failed))
            return false
        }
        videoFinish = null
        elapsed = 0
        dragX = 0f
        dragY = 0f
        rec = Rec(kind, locked)
        return true
    }

    val recordPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val (kind, locked) = pendingStart ?: return@rememberLauncherForActivityResult
        pendingStart = null
        when {
            !hasRecordPermissions(kind) ->
                toast(if (kind == Kind.VIDEO) Res.s(R.string.chat_permission_camera_mic_needed) else Res.s(R.string.chat_permission_mic_needed))
            locked -> beginRecording(kind, locked = true)
            else -> toast(Res.s(R.string.chat_ready_hold_to_record))
        }
    }

    /** Почати запис; якщо бракує дозволів — попросити їх (і повернути false). */
    fun requestRecording(kind: Kind, locked: Boolean): Boolean {
        if (hasRecordPermissions(kind)) return beginRecording(kind, locked)
        pendingStart = kind to locked
        recordPermissions.launch(
            if (kind == Kind.VIDEO) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            else arrayOf(Manifest.permission.RECORD_AUDIO),
        )
        return false
    }

    /** Завершити запис: [keep] — зберегти й обрати час, інакше відкинути. */
    fun finishRecording(keep: Boolean) {
        val r = rec ?: return
        if (r.kind == Kind.VIDEO) {
            videoFinish = keep // результат прийде в onVideoResult
            return
        }
        rec = null
        if (!keep) {
            recorder.cancel()
            return
        }
        val voice = recorder.stop()
        if (voice != null && voice.durationMs >= 700) {
            attachRecording(voice)
        } else {
            voice?.file?.delete()
            toast(Res.s(R.string.chat_hold_longer_to_record))
        }
    }

    fun onVideoResult(video: Attachment?) {
        rec = null
        videoFinish = null
        if (video != null) attachRecording(video)
    }

    // Таймер голосового (для відео час приходить від камери).
    LaunchedEffect(rec?.kind, rec?.startedAt) {
        val r = rec ?: return@LaunchedEffect
        if (r.kind != Kind.VOICE) return@LaunchedEffect
        while (true) {
            elapsed = System.currentTimeMillis() - r.startedAt
            delay(100)
        }
    }

    BackHandler(enabled = rec != null) { finishRecording(keep = false) }

    fun toggleMode() {
        recordMode = if (recordMode == Kind.VIDEO) Kind.VOICE else Kind.VIDEO
        Prefs.setRecordMode(context, recordMode)
    }

    // Підказка один раз: як працює кнопка запису.
    LaunchedEffect(Unit) {
        if (!Prefs.gestureHintShown(context)) {
            Prefs.setGestureHintShown(context)
            delay(800)
            snackbar.showSnackbar(
                Res.s(R.string.chat_gesture_hint),
                duration = SnackbarDuration.Long,
            )
        }
    }

    // Ярлик на головному екрані: одразу запис (з замком).
    LaunchedEffect(quick) {
        val q = quick ?: return@LaunchedEffect
        onQuickConsumed()
        when (q.action) {
            QuickAction.VIDEO -> requestRecording(Kind.VIDEO, locked = true)
            QuickAction.VOICE -> requestRecording(Kind.VOICE, locked = true)
        }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) { GlassSnackbar(it) } },
        topBar = {
            if (rec?.kind != Kind.VIDEO) ChatHeader(
                chat = chat,
                nextIn = scheduled.firstOrNull()?.let { inLabel(it.alarmAt() - nowTick) },
                selectedDate = selectedDate,
                today = today,
                dotsFor = { day -> occurrencesOn(all, day).map { kindColor(it.reminder.kind) }.distinct() },
                onSelectDate = { selectedDate = it },
                onOpenChats = onOpenChats,
                onOpenSearch = onOpenSearch,
                haze = hazeState,
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .edgeFade(top = false)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (rec == null) {
                    attachment?.let { a ->
                        AttachmentPreview(a, player, hazeState, onRemove = {
                            if (player.currentPath == a.file.absolutePath) player.stop()
                            replaceAttachment(null)
                        })
                    }
                }
                Composer(
                    haze = hazeState,
                    text = text,
                    onTextChange = { text = it },
                    canSend = rec == null && (text.isNotBlank() || attachment != null),
                    recordMode = recordMode,
                    rec = rec,
                    elapsed = elapsed,
                    dragX = dragX,
                    dragY = dragY,
                    onAttach = { kind ->
                        when (kind) {
                            Kind.PHOTO -> if (granted(Manifest.permission.CAMERA)) launchPhoto() else photoPermission.launch(Manifest.permission.CAMERA)
                            Kind.VIDEO -> requestRecording(Kind.VIDEO, locked = true)
                            else -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    },
                    onSend = { showSchedule = true },
                    onToggleMode = ::toggleMode,
                    onHoldStart = { requestRecording(recordMode, locked = false) },
                    onDrag = { x, y ->
                        dragX = x
                        dragY = y
                    },
                    onLock = { rec = rec?.copy(locked = true) },
                    onRelease = { finishRecording(keep = true) },
                    onCancel = { finishRecording(keep = false) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            AppBackground()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 6.dp,
                    bottom = padding.calculateBottomPadding() + 10.dp,
                ),
            ) {
                item(key = "banners") { PermissionBanners() }
                if (entries.none { it is Entry.Item }) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyDay(if (selectedDate == today) stringResource(R.string.chat_empty_today) else stringResource(R.string.chat_empty_on_day, dayLabel(selectedDate).replaceFirstChar { it.lowercase() }))
                        }
                    }
                } else {
                    itemsIndexed(entries, key = { _, e ->
                        when (e) {
                            is Entry.Item -> "${e.occurrence.reminder.id}-${e.occurrence.at}"
                            Entry.Now -> "now"
                        }
                    }) { index, e ->
                        val isFirst = index == 0
                        val isLast = index == entries.lastIndex
                        when (e) {
                            Entry.Now -> NowLine(nowTick, isFirst, isLast)
                            is Entry.Item -> {
                                val r = e.occurrence.reminder
                                TimelineItem(
                                    occurrence = e.occurrence,
                                    now = nowTick,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    highlighted = r.id == highlightId,
                                    expanded = r.id == expandedId,
                                    stat = stats[r.id],
                                    player = player,
                                    videoPlaying = r.id == playingVideoId,
                                    onClick = {
                                        if (expandedId == r.id) {
                                            expandedId = null
                                        } else {
                                            expandedId = r.id
                                        }
                                        // Згорнули картку з відео, що грає, — зупиняємо.
                                        if (playingVideoId != null && playingVideoId != expandedId) playingVideoId = null
                                    },
                                    onLongClick = { actionsFor = r },
                                    onOpenPhoto = {
                                        // На весь екран — inline-відтворення зупиняємо, щоб не грало двічі.
                                        player.stop()
                                        playingVideoId = null
                                        viewing = r
                                    },
                                    onPlayVideo = {
                                        player.stop()
                                        playingVideoId = r.id
                                    },
                                    onVideoEnded = { if (playingVideoId == r.id) playingVideoId = null },
                                    expandedContent = {
                                        ReminderExpanded(
                                            reminder = r,
                                            onReschedule = { rescheduling = r },
                                            onDone = { scope.launch { Repo.markDone(r.id) } },
                                            onEditText = { editingText = r },
                                            onMore = { actionsFor = r },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            rec?.takeIf { it.kind == Kind.VIDEO }?.let { r ->
                VideoCircleRecorder(
                    front = frontCamera,
                    finish = videoFinish,
                    locked = r.locked,
                    onFlip = {
                        frontCamera = !frontCamera
                        Prefs.setFrontCamera(context, frontCamera)
                    },
                    onElapsed = { elapsed = it },
                    onResult = ::onVideoResult,
                    modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                )
            }
        }
    }

    // ---- Діалоги ----

    if (showSchedule) {
        ScheduleSheet(
            // Обрано інший день на смужці — пропонуємо саме його (о 9:00).
            initialAt = if (selectedDate != today) selectedDate.atTime(Prefs.defaultTime(context)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
            initialRepeat = Repeat.NONE,
            confirmLabel = stringResource(R.string.chat_schedule_confirm),
            onDismiss = { showSchedule = false },
            alarm = composeAlarm,
            onAlarmChange = { composeAlarm = it },
        ) { at, repeat -> scheduleComposed(at, repeat) }
    }

    rescheduling?.let { r ->
        ScheduleSheet(
            initialAt = if (r.fired) null else r.triggerAt,
            initialRepeat = r.repeat,
            confirmLabel = if (r.fired) stringResource(R.string.chat_remind_again) else stringResource(R.string.chat_save),
            onDismiss = { rescheduling = null },
        ) { at, repeat ->
            rescheduling = null
            selectedDate = at.toLocalDate()
            scope.launch {
                Repo.reschedule(r, at, repeat)
                highlightId = r.id
                snackbar.showSnackbar(Res.s(R.string.chat_snackbar_reminder, soonLabel(at)))
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
        EditTextDialog(r, onDismiss = { editingText = null }) { value ->
            editingText = null
            scope.launch { Repo.editText(r, value) }
        }
    }

    viewing?.let { MediaViewer(it, onDismiss = { viewing = null }) }
}
