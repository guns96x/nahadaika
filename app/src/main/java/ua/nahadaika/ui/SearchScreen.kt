package ua.nahadaika.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import ua.nahadaika.SearchFilter
import ua.nahadaika.SearchHit
import ua.nahadaika.data.Repeat
import ua.nahadaika.data.Repo
import ua.nahadaika.data.alarmAt
import ua.nahadaika.previewText
import ua.nahadaika.searchReminders
import ua.nahadaika.ui.theme.AppBackground
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.glassHaze
import ua.nahadaika.whenLabel

/** Пошук по всіх чатах; фільтр «Виконані» — архів із можливістю очистити. */
@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (chatId: Long, reminderId: Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by Repo.chats.collectAsStateWithLifecycle(emptyList())
    val reminders by Repo.allReminders.collectAsStateWithLifecycle(emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    val hits = remember(reminders, chats, query, filter) { searchReminders(reminders, chats, query, filter) }
    val doneCount = remember(reminders) { reminders.count { it.fired } }

    BackHandler(onBack = onBack)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Поле ще може бути не підключене в першому кадрі — чекаємо кадр і не падаємо, якщо фокус не вдався.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .edgeFade(top = true)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClick = onBack, size = HeaderHeight, haze = hazeState)
                    Spacer(Modifier.width(8.dp))
                    val style = LocalTextStyle.current.copy(color = Glass.Text, fontSize = 16.sp)
                    Row(
                        Modifier.weight(1f).height(HeaderHeight).glassHaze(hazeState).padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = style,
                            singleLine = true,
                            cursorBrush = SolidColor(Glass.Lavender),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text("Пошук по нагадуваннях", style = style, color = Glass.TextFaint)
                                    inner()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier.size(HeaderHeight - 8.dp).clickable { query = "" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Close, "Очистити", tint = Glass.TextDim) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchFilter.entries.forEach { f ->
                        FilterChip(f.label, selected = f == filter) { filter = f }
                    }
                    Spacer(Modifier.weight(1f))
                    if (filter == SearchFilter.DONE && doneCount > 0) {
                        GlassIconButton(Icons.Default.DeleteSweep, "Очистити виконані", onClick = { confirmClear = true }, haze = hazeState)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            AppBackground()
            if (hits.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Тут з'являтимуться нагадування" else "Нічого не знайдено",
                        color = Glass.TextDim,
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = padding.calculateTopPadding() + 6.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(hits, key = { it.reminder.id }) { hit ->
                        HitRow(hit) { onOpen(hit.reminder.chatId, hit.reminder.id) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистити виконані?") },
            text = { Text("Буде видалено $doneCount виконаних нагадувань разом із записами. Це не можна скасувати.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        val n = Repo.clearDone()
                        Toast.makeText(context, "Видалено: $n", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Очистити", color = Glass.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(Glass.Pill)
            .background(if (selected) Glass.Primary else Glass.FillStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Glass.OnPrimary else Glass.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    val r = hit.reminder
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hit.chat?.let { Avatar(it, size = 34) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                previewText(r).ifBlank { "Нагадування" },
                color = Glass.Text,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.repeat != Repeat.NONE) {
                    Icon(Icons.Default.Repeat, null, Modifier.size(14.dp), tint = Glass.TextDim)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    listOfNotNull(
                        hit.chat?.name,
                        if (r.fired) "виконано ${whenLabel(r.lastFiredAt ?: r.triggerAt)}" else whenLabel(r.alarmAt()),
                    ).joinToString(" · "),
                    color = Glass.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            if (r.fired) Icons.Default.Check else Icons.Default.Schedule,
            contentDescription = null,
            tint = Glass.TextDim,
            modifier = Modifier.size(20.dp),
        )
    }
}
