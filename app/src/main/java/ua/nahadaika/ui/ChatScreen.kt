package ua.nahadaika.ui

import android.Manifest
import androidx.compose.foundation.lazy.itemsIndexed
import ua.nahadaika.Occurrence
import ua.nahadaika.inLabel
import ua.nahadaika.occurrencesOn
import java.time.ZoneId
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glassHaze
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ua.nahadaika.ui.theme.ThemeModeButton
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.GlassSnackbar
import ua.nahadaika.ui.theme.sheetGlow
import ua.nahadaika.ui.theme.glass
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import ua.nahadaika.Prefs
import ua.nahadaika.VoiceParser
import ua.nahadaika.media.LiveDictation
import ua.nahadaika.media.Transcriber
import ua.nahadaika.media.Hearing
import ua.nahadaika.media.OfflineSpeech
import ua.nahadaika.media.SpeechPack
import kotlinx.coroutines.Job
import androidx.compose.material3.CircularProgressIndicator
import ua.nahadaika.previewText
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chat by Repo.chat(chatId).collectAsStateWithLifecycle(null)
    val all by Repo.reminders(chatId).collectAsStateWithLifecycle(emptyList())

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
    // Фонове розпізнавання щойно записаного; голосова команда без вікна Google.
    var transcription by remember { mutableStateOf<Job?>(null) }
    var offerSpeech by remember { mutableStateOf(false) }
    val dictation = remember { LiveDictation(context) }
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
            dictation.cancel()
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
        if (new !== attachment) {
            transcription?.cancel()
            transcription = null
        }
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
            val result = snackbar.showSnackbar(
                message = "Нагадаю ${whenLabel(reminder.triggerAt)}" + if (label.isNotBlank()) ": $label" else "",
                actionLabel = "Змінити",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) rescheduling = reminder.copy(id = id)
        }
    }

    fun scheduleComposed(at: Long, repeat: Repeat, textOverride: String? = null) {
        val a = attachment
        val body = (textOverride ?: text).trim()
        val reminder = Reminder(
            chatId = chatId,
            kind = a?.kind ?: Kind.TEXT,
            text = if (a == null) body.ifEmpty { "Нагадування" } else body,
            mediaPath = a?.file?.absolutePath,
            durationMs = a?.durationMs ?: 0,
            triggerAt = at,
            repeat = repeat,
        )
        if (a != null && player.currentPath == a.file.absolutePath) player.stop()
        attachment = null
        text = ""
        showSchedule = false
        schedule(reminder)
    }

    // ---- Голосова команда: «нагадай завтра о 9 купити хліб» ----

    /** Розібрати сказане: є час — одразу планувати, немає — лишити текст і відкрити вибір часу. */
    fun applySpoken(spoken: String) {
        val cmd = VoiceParser.parse(spoken, defaultTime = Prefs.defaultTime(context))
        val combined = listOf(text.trim(), cmd.text).filter { it.isNotBlank() }.joinToString(" ")
        if (cmd.at != null) {
            scheduleComposed(cmd.at, cmd.repeat, combined)
        } else {
            text = combined
            showSchedule = true
            toast("Почув: «$spoken» — але не зрозумів, коли нагадати")
        }
    }

    // Запасний варіант — вікно Google, якщо фонового розпізнавання на телефоні немає.
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) applySpoken(spoken)
    }

    fun startDictation() {
        player.stop()
        playingVideoId = null
        showSchedule = false
        if (dictation.isAvailable()) {
            dictation.start(onResult = ::applySpoken, onError = ::toast)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Наприклад: «нагадай завтра о 9 купити хліб»")
        try {
            speech.launch(intent)
        } catch (_: ActivityNotFoundException) {
            toast("На телефоні немає розпізнавання мовлення (потрібен застосунок Google)")
        }
    }

    val dictatePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDictation() else toast("Потрібен доступ до мікрофона")
    }

    fun dictate() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startDictation()
        } else {
            showSchedule = false
            dictatePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---- Записали голосове чи відео: у фоні дізнатися, коли нагадати ----

    fun cancelTranscription() {
        transcription?.cancel()
        transcription = null
    }

    /** Прикріпити запис і розпізнати в ньому час («завтра о 9…»); не вийшло — відкрити вибір часу. */
    fun attachRecording(a: Attachment) {
        cancelTranscription()
        replaceAttachment(a)
        // Автоматичне розпізнавання вимкнене в налаштуваннях — одразу вибір часу.
        if (!Prefs.autoSchedule(context)) {
            showSchedule = true
            return
        }
        transcription = scope.launch {
            val defaultTime = Prefs.defaultTime(context)
            val hearing = Transcriber.transcribe(context, a.file) { VoiceParser.parse(it, defaultTime = defaultTime).at != null }
            transcription = null
            if (attachment !== a) return@launch
            val cmd = (hearing as? Hearing.Heard)?.let { VoiceParser.parse(it.text, defaultTime = defaultTime) }
            if (cmd?.at != null) {
                val caption = if (Prefs.voiceCaption(context)) cmd.text else ""
                scheduleComposed(cmd.at, cmd.repeat, listOf(text.trim(), caption).filter { it.isNotBlank() }.joinToString(" "))
                return@launch
            }
            when {
                hearing is Hearing.Heard -> toast("Почув: «${hearing.text}» — але не зрозумів, коли нагадати")
                hearing == Hearing.Nothing -> toast("Не розчув у записі, коли нагадати")
                OfflineSpeech.state is OfflineSpeech.State.Downloading ->
                    toast("Розпізнавання ще завантажується — оберіть час цього разу")
                !Prefs.speechOfferShown(context) -> {
                    // Один раз пояснюємо, чому не розпізнало, і пропонуємо офлайн-розпізнавання.
                    offerSpeech = true
                    return@launch
                }
            }
            showSchedule = true
        }
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
            if (ok) toast("Камера не зберегла фото")
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

    fun launchPhoto() {
        val file = MediaFiles.newFile(context, "jpg")
        pendingPhoto = Attachment(Kind.PHOTO, file)
        try {
            takePhoto.launch(MediaFiles.uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            pendingPhoto = null
            file.delete()
            toast("Не знайдено застосунок камери")
        }
    }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPhoto() else toast("Потрібен доступ до камери")
    }

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasRecordPermissions(kind: Kind) =
        granted(Manifest.permission.RECORD_AUDIO) && (kind != Kind.VIDEO || granted(Manifest.permission.CAMERA))

    fun beginRecording(kind: Kind, locked: Boolean): Boolean {
        player.stop()
        dictation.cancel()
        // Поки йде запис, підвантажуємо офлайн-розпізнавач — тоді час розпізнається одразу після запису.
        if (OfflineSpeech.isReady) scope.launch { OfflineSpeech.warmUp() }
        playingVideoId = null
        if (kind == Kind.VOICE && !recorder.start()) {
            toast("Не вдалося увімкнути мікрофон")
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
                toast(if (kind == Kind.VIDEO) "Потрібен доступ до камери й мікрофона" else "Потрібен доступ до мікрофона")
            locked -> beginRecording(kind, locked = true)
            else -> toast("Готово! Утримуйте кнопку, щоб записати")
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
            toast("Утримуйте кнопку довше, щоб записати")
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
                "Тап — відео ↔ голосове. Утримуйте — запис, потягніть угору — 🔒",
                duration = SnackbarDuration.Long,
            )
        }
    }

    // Ярлик на головному екрані: одразу запис (з замком) або голосова команда.
    LaunchedEffect(quick) {
        val q = quick ?: return@LaunchedEffect
        onQuickConsumed()
        when (q.action) {
            QuickAction.VIDEO -> requestRecording(Kind.VIDEO, locked = true)
            QuickAction.VOICE -> requestRecording(Kind.VOICE, locked = true)
            QuickAction.DICTATE -> dictate()
        }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) { GlassSnackbar(it) } },
        topBar = {
            // Окремі скляні капсули однакової висоти, як у Telegram: ☰ | чат | тема.
            if (rec?.kind != Kind.VIDEO) Column(
                Modifier
                    .fillMaxWidth()
                    .edgeFade(top = true)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Default.Menu, "Усі чати", onClick = onOpenChats, size = HeaderHeight, haze = hazeState)
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .weight(1f)
                            .height(HeaderHeight)
                            .glassHaze(hazeState)
                            .clickable(onClick = onOpenChats)
                            .padding(start = 6.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chat?.let { c ->
                            Avatar(c, size = 44)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    c.name,
                                    color = Glass.Text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    scheduled.firstOrNull()?.let { inLabel(it.alarmAt() - nowTick) } ?: "немає запланованих",
                                    color = Glass.TextDim,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ThemeModeButton(size = HeaderHeight, haze = hazeState)
                }
                DayStrip(
                    selected = selectedDate,
                    today = today,
                    dotsFor = { day -> occurrencesOn(all, day).map { kindColor(it.reminder.kind) }.distinct() },
                    onSelect = { selectedDate = it },
                    haze = hazeState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        AttachmentPreview(a, player, hazeState, recognizing = transcription != null, onRemove = {
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
                    onDictate = ::dictate,
                    listening = dictation.listening,
                    heard = dictation.partial,
                    onDictationDone = dictation::stop,
                    onDictationCancel = dictation::cancel,
                    onSend = {
                        cancelTranscription()
                        showSchedule = true
                    },
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
                            EmptyDay(if (selectedDate == today) "На сьогодні" else "На ${dayLabel(selectedDate).replaceFirstChar { it.lowercase() }}")
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
                                    player = player,
                                    videoPlaying = r.id == playingVideoId,
                                    onClick = { actionsFor = r },
                                    onOpenPhoto = { viewing = r },
                                    onPlayVideo = {
                                        player.stop()
                                        playingVideoId = r.id
                                    },
                                    onVideoEnded = { if (playingVideoId == r.id) playingVideoId = null },
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

    if (offerSpeech) {
        fun close() {
            Prefs.setSpeechOfferShown(context)
            offerSpeech = false
            showSchedule = true
        }
        AlertDialog(
            onDismissRequest = ::close,
            title = { Text("Розпізнавати час у голосових?") },
            text = {
                Text(
                    "Цей телефон не вміє сам розпізнавати записані голосові й відео. " +
                        "Можна один раз завантажити офлайн-розпізнавання української — " +
                        "≈${SpeechPack.UK.downloadMb} МБ (на телефоні ≈${SpeechPack.UK.diskMb} МБ), краще через Wi-Fi.\n\n" +
                        "Після цього «завтра о 9…» в записі ставитиметься саме, навіть без інтернету. " +
                        "А поки що — оберіть час вручну.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    OfflineSpeech.download()
                    close()
                }) { Text("Завантажити") }
            },
            dismissButton = { TextButton(onClick = ::close) { Text("Не зараз") } },
        )
    }

    if (showSchedule) {
        ScheduleSheet(
            // Обрано інший день на смужці — пропонуємо саме його (о 9:00).
            initialAt = if (selectedDate != today) selectedDate.atTime(Prefs.defaultTime(context)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
            initialRepeat = Repeat.NONE,
            confirmLabel = "Запланувати",
            onDismiss = { showSchedule = false },
            onDictate = ::dictate,
        ) { at, repeat -> scheduleComposed(at, repeat) }
    }

    rescheduling?.let { r ->
        ScheduleSheet(
            initialAt = if (r.fired) null else r.triggerAt,
            initialRepeat = r.repeat,
            confirmLabel = if (r.fired) "Нагадати ще раз" else "Зберегти",
            onDismiss = { rescheduling = null },
        ) { at, repeat ->
            rescheduling = null
            selectedDate = at.toLocalDate()
            scope.launch {
                Repo.reschedule(r, at, repeat)
                highlightId = r.id
                snackbar.showSnackbar("Нагадаю ${whenLabel(at)}")
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

private val HeaderHeight = 56.dp
private val BarHeight = 52.dp

/** Поточний запис: що пишемо і чи «замкнено» (пишеться без утримання). */
private data class Rec(val kind: Kind, val locked: Boolean, val startedAt: Long = System.currentTimeMillis())

private val LockDistance = 90.dp
private val CancelDistance = 120.dp

@Composable
private fun Composer(
    haze: HazeState,
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    recordMode: Kind,
    rec: Rec?,
    elapsed: Long,
    dragX: Float,
    dragY: Float,
    onAttach: (Kind?) -> Unit,
    onDictate: () -> Unit,
    listening: Boolean,
    heard: String,
    onDictationDone: () -> Unit,
    onDictationCancel: () -> Unit,
    onSend: () -> Unit,
    onToggleMode: () -> Unit,
    onHoldStart: () -> Boolean,
    onDrag: (Float, Float) -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
            if (rec != null) {
                RecordingPill(haze, rec, elapsed, dragX, onCancel)
            } else if (listening) {
                ListeningPill(haze, heard, onDictationCancel)
            } else {
                InputPill(haze, text, onTextChange, onAttach, onDictate)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Одна й та сама кнопка весь час — щоб жест утримання не переривався.
        RecordButton(
            canSend = canSend || listening,
            done = listening,
            recordMode = recordMode,
            rec = rec,
            dragY = dragY,
            onSend = if (listening) onDictationDone else onSend,
            onToggleMode = onToggleMode,
            onHoldStart = onHoldStart,
            onDrag = onDrag,
            onLock = onLock,
            onRelease = onRelease,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun InputPill(
    haze: HazeState,
    text: String,
    onTextChange: (String) -> Unit,
    onAttach: (Kind?) -> Unit,
    onDictate: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Іконки прив'язані до низу, як у Telegram: при багаторядковому тексті вони лишаються біля кнопки.
    Row(
        Modifier.fillMaxWidth().heightIn(min = BarHeight).glassHaze(haze, RoundedCornerShape(BarHeight / 2)),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box {
            BarIcon(Icons.Default.AttachFile, "Додати", Glass.TextDim) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Default.Videocam, "Записати відео") { menu = false; onAttach(Kind.VIDEO) }
                MenuItem(Icons.Default.CameraAlt, "Зняти фото") { menu = false; onAttach(Kind.PHOTO) }
                MenuItem(Icons.Default.PhotoLibrary, "З галереї") { menu = false; onAttach(null) }
            }
        }
        val style = LocalTextStyle.current.copy(color = Glass.Text, fontSize = 16.sp, lineHeight = 22.sp)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = style,
            cursorBrush = SolidColor(Glass.Lavender),
            maxLines = 6,
            modifier = Modifier.weight(1f).padding(vertical = 15.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Нагадування", style = style, color = Glass.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        BarIcon(Icons.Default.RecordVoiceOver, "Сказати нагадування", Glass.Lavender, onClick = onDictate)
    }
}

/** Голосова команда без вікна Google: «Слухаю…», сказане з'являється наживо; ✕ — скасувати. */
@Composable
private fun ListeningPill(haze: HazeState, heard: String, onCancel: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "listen").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = BarHeight).glassHaze(haze, RoundedCornerShape(BarHeight / 2)).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).background(Glass.Lavender, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            heard.ifBlank { "Слухаю… «завтра о 9 купити хліб»" },
            color = if (heard.isBlank()) Glass.TextDim else Glass.Text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
        )
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Скасувати", tint = Glass.TextDim) }
    }
}

/** Смуга запису: ● таймер і «‹ посуньте, щоб скасувати» (або «Скасувати» в режимі 🔒). */
@Composable
private fun RecordingPill(haze: HazeState, rec: Rec, elapsed: Long, dragX: Float, onCancel: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    val cancelPx = with(LocalDensity.current) { CancelDistance.toPx() }
    Row(
        Modifier.fillMaxWidth().height(BarHeight).glassHaze(haze, RoundedCornerShape(BarHeight / 2)).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).background(Glass.Danger, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuration(elapsed),
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            color = Glass.Text,
            fontSize = 16.sp,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (rec.locked) {
                TextButton(onClick = onCancel) { Text("Скасувати", color = Glass.Danger, fontSize = 15.sp) }
            } else {
                val progress = (-dragX / cancelPx).coerceIn(0f, 1f)
                Row(
                    Modifier
                        .offset { IntOffset((dragX * 0.6f).toInt(), 0) }
                        .alpha(1f - progress * 0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Glass.TextFaint, modifier = Modifier.size(20.dp))
                    Text("Посуньте, щоб скасувати", color = Glass.TextDim, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Кнопка запису, як у Telegram: тап — перемкнути 🎤 ↔ 📹; утримання — запис (відпустили — готово);
 * утримуючи, потягнути вгору до 🔒 — запис без рук; потягнути вліво — скасувати.
 * Коли є текст або вкладення — кнопка «запланувати»; у режимі 🔒 — «готово».
 */
@Composable
private fun RecordButton(
    canSend: Boolean,
    done: Boolean,
    recordMode: Kind,
    rec: Rec?,
    dragY: Float,
    onSend: () -> Unit,
    onToggleMode: () -> Unit,
    onHoldStart: () -> Boolean,
    onDrag: (Float, Float) -> Unit,
    onLock: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lockPx = with(density) { LockDistance.toPx() }
    val cancelPx = with(density) { CancelDistance.toPx() }
    val state by rememberUpdatedState(Triple(canSend, rec, recordMode))
    val callbacks by rememberUpdatedState(
        listOf(onSend, onToggleMode, onLock, onRelease, onCancel),
    )
    val holdStart by rememberUpdatedState(onHoldStart)
    val drag by rememberUpdatedState(onDrag)

    val holding = rec != null && !rec.locked
    val scale by animateFloatAsState(if (holding) 1.2f else 1f, label = "scale")

    Box(contentAlignment = Alignment.Center) {
        if (holding) LockHint(progress = (-dragY / lockPx).coerceIn(0f, 1f))
        Box(
            Modifier
                .size(BarHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Glass.Primary)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val (send, current, _) = state
                        val (sendCb, toggleCb, lockCb, releaseCb, cancelCb) = callbacks
                        if (send || current?.locked == true) {
                            // Звичайна кнопка: «запланувати» або «готово» в режимі 🔒.
                            if (waitForUpOrCancellation() != null) {
                                if (send) sendCb() else releaseCb()
                            }
                            return@awaitEachGesture
                        }
                        // Короткий тап чи утримання?
                        val tap = withTimeoutOrNull(220L) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                                if (!change.pressed) return@withTimeoutOrNull true
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    return@withTimeoutOrNull false
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        } ?: false
                        if (tap) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            toggleCb()
                            return@awaitEachGesture
                        }
                        if (!holdStart()) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                releaseCb()
                                break
                            }
                            change.consume()
                            val d = change.position - down.position
                            drag(d.x.coerceAtMost(0f), d.y.coerceAtMost(0f))
                            if (-d.y > lockPx) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                lockCb()
                                // Палець ще на екрані — просто чекаємо, поки відпустять.
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                                break
                            }
                            if (-d.x > cancelPx) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                cancelCb()
                                do {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                } while (e.changes.any { it.pressed })
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                done -> Icons.Default.Check
                canSend -> Icons.Default.AlarmAdd
                rec?.locked == true -> Icons.Default.Check
                (rec?.kind ?: recordMode) == Kind.VIDEO -> Icons.Default.Videocam
                else -> Icons.Default.Mic
            }
            Crossfade(targetState = icon, label = "icon") { current ->
                Icon(
                    current,
                    contentDescription = when (current) {
                        Icons.Default.AlarmAdd -> "Запланувати"
                        Icons.Default.Check -> "Готово"
                        Icons.Default.Videocam -> "Відео: тап — перемкнути, утримати — записати"
                        else -> "Голосове: тап — перемкнути, утримати — записати"
                    },
                    tint = Glass.OnPrimary,
                )
            }
        }
    }
}

/** Скляна «капсула» з замочком над кнопкою: тягніть угору, щоб писати без утримання. */
@Composable
private fun LockHint(progress: Float) {
    Column(
        Modifier
            .offset { IntOffset(0, (-(BarHeight.toPx() * 1.55f) - progress * 36.dp.toPx()).toInt()) }
            .width(40.dp)
            .glass(RoundedCornerShape(20.dp), Glass.Sheet)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (progress > 0.85f) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = "Потягніть угору, щоб зафіксувати",
            tint = if (progress > 0.85f) Glass.Lavender else Glass.Text,
            modifier = Modifier.size(20.dp),
        )
        Icon(Icons.Default.KeyboardArrowUp, null, tint = Glass.TextFaint, modifier = Modifier.size(18.dp).alpha(1f - progress))
    }
}

/** Іконка в полі вводу: квадрат висотою з поле, щоб усе стояло на одній лінії. */
@Composable
private fun BarIcon(icon: ImageVector, contentDescription: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(BarHeight).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun AttachmentPreview(attachment: Attachment, player: AudioPlayer, haze: HazeState, recognizing: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassHaze(haze, RoundedCornerShape(22.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (attachment.kind) {
            Kind.PHOTO, Kind.VIDEO -> AsyncImage(
                model = attachment.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
            )
            Kind.VOICE -> {
                val path = attachment.file.absolutePath
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Glass.Primary).clickable { player.toggle(path) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (player.currentPath == path && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Прослухати",
                        tint = Glass.OnPrimary,
                    )
                }
            }
            Kind.TEXT -> Unit
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when (attachment.kind) {
                    Kind.PHOTO -> "Фото"
                    Kind.VIDEO -> "Відео · ${formatDuration(attachment.durationMs)}"
                    Kind.VOICE -> "Голосове · ${formatDuration(attachment.durationMs)}"
                    Kind.TEXT -> ""
                },
                color = Glass.Text,
            )
            if (recognizing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                    CircularProgressIndicator(Modifier.size(11.dp), color = Glass.Lavender, strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Розпізнаю, коли нагадати…", color = Glass.TextDim, fontSize = 13.sp)
                }
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Прибрати", tint = Glass.TextDim) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Glass.Sheet, scrimColor = Glass.Scrim) {
        Column(
            Modifier.sheetGlow().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (reminder.fired) "Надіслано ${whenLabel(reminder.lastFiredAt ?: reminder.triggerAt)}"
                else "Нагадаю ${whenLabel(reminder.alarmAt())}",
                color = Glass.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                    Text("Видалити", color = Glass.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) Glass.Danger else Glass.Text
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
