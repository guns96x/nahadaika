package ua.nahadaika.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.nahadaika.Occurrence
import ua.nahadaika.Prefs
import ua.nahadaika.VoiceCommand
import ua.nahadaika.VoiceParser
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Reminder
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.data.alarmAt
import ua.nahadaika.dayLabel
import ua.nahadaika.inLabel
import ua.nahadaika.media.Attachment
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.media.Hearing
import ua.nahadaika.media.LiveDictation
import ua.nahadaika.media.MediaFiles
import ua.nahadaika.media.OfflineSpeech
import ua.nahadaika.media.SpeechPack
import ua.nahadaika.media.Transcriber
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
    // Нове нагадування — як будильник (гучно, на весь екран).
    var composeAlarm by remember { mutableStateOf(false) }
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
                message = (if (reminder.alarm) "⏰ Будильник ${soonLabel(reminder.triggerAt)}" else "Нагадаю ${soonLabel(reminder.triggerAt)}") +
                    if (label.isNotBlank() && label != "Будильник" && label != "Таймер") ": $label" else "",
                actionLabel = "Змінити",
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
            text = if (a == null) body.ifEmpty { "Нагадування" } else body,
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

    /**
     * Кілька нагадувань з однієї фрази. Якщо до них записане голосове чи відео — кожне отримує свою копію файлу
     * (видалення одного нагадування не зачепить інші).
     */
    fun scheduleMany(cmds: List<VoiceCommand>, media: Attachment?) {
        val a = media
        if (a != null && player.currentPath == a.file.absolutePath) player.stop()
        if (a != null && attachment === a) attachment = null
        showSchedule = false
        composeAlarm = false
        val caption = a == null || Prefs.voiceCaption(context)
        scope.launch {
            val ids = cmds.mapIndexed { i, cmd ->
                val file = when {
                    a == null -> null
                    i == 0 -> a.file
                    else -> withContext(Dispatchers.IO) { MediaFiles.newFile(context, a.file.extension).also { a.file.copyTo(it, overwrite = true) } }
                }
                Repo.createReminder(
                    Reminder(
                        chatId = chatId,
                        kind = a?.kind ?: Kind.TEXT,
                        text = if (a == null) cmd.text.ifEmpty { "Нагадування" } else if (caption) cmd.text else "",
                        mediaPath = file?.absolutePath,
                        durationMs = a?.durationMs ?: 0,
                        triggerAt = cmd.at!!,
                        repeat = cmd.repeat,
                        alarm = cmd.alarm,
                    ),
                )
            }
            selectedDate = cmds.first().at!!.toLocalDate()
            highlightId = ids.first()
            val n = cmds.size
            val word = if (n % 10 in 2..4 && n % 100 !in 12..14) "нагадування" else "нагадувань"
            snackbar.showSnackbar(
                "Поставив $n $word: " + cmds.joinToString("; ") { c ->
                    soonLabel(c.at!!) + if (c.text.isNotBlank()) " — ${c.text.take(24)}" else ""
                },
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        }
    }

    // ---- Голосова команда: «нагадай завтра о 9 купити хліб» ----

    /** Розібрати сказане: є час — одразу планувати, немає — лишити текст і відкрити вибір часу. */
    fun applySpoken(spoken: String) {
        val many = VoiceParser.parseMany(spoken, defaultTime = Prefs.defaultTime(context))
        if (many.size > 1 && many.all { it.at != null }) {
            scheduleMany(many, media = null)
            return
        }
        val cmd = VoiceParser.parse(spoken, defaultTime = Prefs.defaultTime(context))
        val combined = listOf(text.trim(), cmd.text).filter { it.isNotBlank() }.joinToString(" ")
        if (cmd.at != null) {
            scheduleComposed(cmd.at, cmd.repeat, combined, alarm = cmd.alarm)
        } else {
            text = combined
            if (cmd.alarm) composeAlarm = true
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
            val many = (hearing as? Hearing.Heard)?.let { VoiceParser.parseMany(it.text, defaultTime = defaultTime) }.orEmpty()
            if (many.size > 1 && many.all { it.at != null }) {
                scheduleMany(many, a)
                return@launch
            }
            val cmd = (hearing as? Hearing.Heard)?.let { VoiceParser.parse(it.text, defaultTime = defaultTime) }
            if (cmd?.at != null) {
                val caption = if (Prefs.voiceCaption(context)) cmd.text else ""
                scheduleComposed(cmd.at, cmd.repeat, listOf(text.trim(), caption).filter { it.isNotBlank() }.joinToString(" "), alarm = cmd.alarm)
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
            alarm = composeAlarm,
            onAlarmChange = { composeAlarm = it },
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
                snackbar.showSnackbar("Нагадаю ${soonLabel(at)}")
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
