package ua.nahadaika.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import ua.nahadaika.Occurrence
import ua.nahadaika.data.Kind
import ua.nahadaika.data.Repeat
import ua.nahadaika.formatTime
import ua.nahadaika.inLabel
import ua.nahadaika.media.AudioPlayer
import ua.nahadaika.repeatLabel
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.glass
import ua.nahadaika.ui.theme.card
import ua.nahadaika.ui.theme.glassHaze
import ua.nahadaika.ui.theme.liquidLens
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import ua.nahadaika.R
import ua.nahadaika.data.CommentStat
import ua.nahadaika.hasMoreThanTitle
import ua.nahadaika.reminderTitle

/** Колір типу нагадування на лінії — однаковий у темній і світлій темі. */
fun kindColor(kind: Kind): Color = when (kind) {
    Kind.TEXT -> Color(0xFF8E7CFF)
    Kind.VOICE -> Color(0xFF2BB3A3)
    Kind.VIDEO -> Color(0xFFFF6B5B)
    Kind.PHOTO -> Color(0xFFF5A524)
}

private fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.TEXT -> Icons.Default.NotificationsNone
    Kind.VOICE -> Icons.Default.Mic
    Kind.VIDEO -> Icons.Default.Videocam
    Kind.PHOTO -> Icons.Default.Image
}

private const val PAST_DAYS = 14L
private const val FUTURE_DAYS = 120L

/** Крок між днями на нитці й висота смужки. */
private val CELL_WIDTH = 40.dp
private val STRIP_HEIGHT = 64.dp

/** У центрі число збільшується до [CELL_MAX_SCALE] разів — ніби під краплею-лінзою. */
private const val CELL_MAX_SCALE = 1.35f

/** Крапля-намистина над обраним днем і нитка, на яку нанизані дні (нижче за числа). */
private val BEAD_SIZE = 58.dp
private val THREAD_BELOW_CENTER = 11.dp
private val DOTS_BELOW_CENTER = 16.dp
private val WEEKDAY_ABOVE_CENTER = 13.dp

/** Півширина лінзи в клітинках: далі за неї дні вже звичайного розміру. */
private const val LENS_HALF_WIDTH = 2.6f

/** Наскільки клітинка на відстані [t] клітинок від центру збільшена: 1 у центрі, 0 за межами лінзи. */
private fun lensBump(t: Float): Float =
    if (kotlin.math.abs(t) >= LENS_HALF_WIDTH) 0f else (1f + kotlin.math.cos(Math.PI.toFloat() * t / LENS_HALF_WIDTH)) / 2f

/**
 * Інтеграл [lensBump] від центру до [t]: на стільки клітинок зсувається день, щоб збільшені сусіди
 * не налазили один на одного й не лишали щілин — нитка суцільна, лише опукла посередині.
 */
private fun lensShift(t: Float): Float {
    val w = LENS_HALF_WIDTH
    if (kotlin.math.abs(t) >= w) return kotlin.math.sign(t) * w / 2f
    return (t + w / Math.PI.toFloat() * kotlin.math.sin(Math.PI.toFloat() * t / w)) / 2f
}

/**
 * Смужка днів як нитка з намистиною: числа йдуть уздовж тонкої лінії, обраний день — у скляній краплі
 * посередині, сусіди плавно збільшуються до неї. Гортання прилипає до центру, і день посередині стає обраним;
 * тап по дню — теж центрує його.
 */
@Composable
fun DayStrip(
    selected: LocalDate,
    today: LocalDate,
    dotsFor: (LocalDate) -> List<Color>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = today.minusDays(PAST_DAYS)
    val count = (PAST_DAYS + FUTURE_DAYS + 1).toInt()
    val indexOf = { d: LocalDate -> ChronoUnit.DAYS.between(start, d).toInt().coerceIn(0, count - 1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(selected))
    val currentSelected by rememberUpdatedState(selected)
    val onSelectNow by rememberUpdatedState(onSelect)

    // Індекс клітинки, чий центр найближчий до центру смужки.
    fun centeredIndex(): Int? {
        val info = listState.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - middle) }?.index
    }

    // Обрали день не гортанням (тап, кнопка «Сьогодні», нагадування зі сповіщення) — центруємо його.
    LaunchedEffect(selected) {
        val i = indexOf(selected)
        if (centeredIndex() != i && !listState.isScrollInProgress) listState.animateScrollToItem(i)
    }
    // Прогортали й зупинились — день посередині стає обраним.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect {
            val c = centeredIndex() ?: return@collect
            if (c != indexOf(currentSelected)) onSelectNow(start.plusDays(c.toLong()))
        }
    }

    val thread = Glass.TextFaint
    BoxWithConstraints(modifier.height(STRIP_HEIGHT), contentAlignment = Alignment.Center) {
        val sidePadding = (maxWidth - CELL_WIDTH) / 2
        val density = LocalDensity.current
        val pitchPx = with(density) { CELL_WIDTH.toPx() }

        // Відстань клітинки [i] від центру в клітинках (null — її не видно).
        fun offsetOf(i: Int): Float? {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return null
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            return (item.offset + item.size / 2f - middle) / pitchPx
        }

        // Нитка: тонка лінія під числами, що згасає до країв і заходить у краплю з боків.
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f + THREAD_BELOW_CENTER.toPx()
            val r = BEAD_SIZE.toPx() / 2f
            val dy = THREAD_BELOW_CENTER.toPx()
            val gap = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
            val cx = size.width / 2f
            val brush = Brush.horizontalGradient(
                0f to Color.Transparent, 0.18f to thread, 0.82f to thread, 1f to Color.Transparent,
            )
            val stroke = 1.2.dp.toPx()
            drawLine(brush, Offset(0f, y), Offset(cx - gap, y), stroke, StrokeCap.Round)
            drawLine(brush, Offset(cx + gap, y), Offset(size.width, y), stroke, StrokeCap.Round)
        }
        // Крапля-намистина під центральним числом (текст лишається поверх — чіткий).
        Box(Modifier.size(BEAD_SIZE).liquidLens(CircleShape))

        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(count) { i ->
                val day = start.plusDays(i.toLong())
                DayCell(
                    day = day,
                    selected = day == selected,
                    today = day == today,
                    dots = dotsFor(day),
                    lens = { offsetOf(i)?.let(::lensBump) ?: 0f },
                    modifier = Modifier.graphicsLayer {
                        // Лінза: розмір = 1 + (M−1)·bump, зсув = (M−1)·∫bump — дні йдуть упритул, центр опуклий.
                        val t = offsetOf(i) ?: return@graphicsLayer
                        val bump = lensBump(t)
                        val scale = 1f + (CELL_MAX_SCALE - 1f) * bump
                        scaleX = scale
                        scaleY = scale
                        val shown = t + (CELL_MAX_SCALE - 1f) * lensShift(t)
                        // Біля країв нитка з числами м'яко згасає, а не обривається напівклітинкою.
                        val info = listState.layoutInfo
                        val half = (info.viewportEndOffset - info.viewportStartOffset) / 2f / pitchPx
                        val edge = (half - kotlin.math.abs(shown) - 0.4f).coerceIn(0f, 1f)
                        alpha = (0.5f + 0.5f * bump) * edge
                        translationX = (shown - t) * pitchPx
                    },
                    onClick = { onSelect(day) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    today: Boolean,
    dots: List<Color>,
    lens: () -> Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .width(CELL_WIDTH)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.chat_show_day),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // День тижня проступає лише під краплею — на нитці самі числа, як на рисунку.
        Text(
            day.format(DateTimeFormatter.ofPattern("EE", Locale.getDefault())).replaceFirstChar { it.uppercase() },
            fontSize = 9.sp,
            lineHeight = 10.sp,
            color = if (today) Glass.Lavender else Glass.TextDim,
            modifier = Modifier
                .offset(y = -WEEKDAY_ABOVE_CENTER)
                .graphicsLayer { alpha = ((lens() - 0.75f) / 0.25f).coerceIn(0f, 1f) },
        )
        Text(
            "${day.dayOfMonth}",
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                today -> Glass.Lavender
                selected -> Glass.Text
                else -> Glass.TextDim
            },
            modifier = Modifier.offset(y = (-1).dp),
        )
        Row(Modifier.offset(y = DOTS_BELOW_CENTER), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.take(3).forEach { Box(Modifier.size(3.5.dp).background(it, CircleShape)) }
        }
    }
}

private val TimeColumn = 42.dp
private val NodeColumn = 40.dp
private val NodeSize = 28.dp
private val SidePadding = 12.dp

/** Пунктирна лінія таймлайну через увесь рядок (обрізана біля першого й останнього кружка). */
@Composable
private fun Modifier.timelineLine(isFirst: Boolean, isLast: Boolean): Modifier {
    val color = Glass.TextFaint.copy(alpha = 0.35f)
    return drawBehind {
        val x = (SidePadding + TimeColumn + NodeColumn / 2).toPx()
        val nodeCenter = (2.dp + NodeSize / 2).toPx()
        drawLine(
            color,
            Offset(x, if (isFirst) nodeCenter else 0f),
            Offset(x, if (isLast) nodeCenter else size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )
    }
}

/**
 * Нагадування на лінії: час ліворуч, кольоровий кружок і картка-акордеон.
 * Згорнута — лише автоматичний заголовок і значки; тап розгортає повний текст, медіа, дії та обговорення
 * ([expandedContent]). Розгорнутою буває одна картка — перемикає [onClick] у чаті.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineItem(
    occurrence: Occurrence,
    now: Long,
    isFirst: Boolean,
    isLast: Boolean,
    highlighted: Boolean,
    expanded: Boolean,
    stat: CommentStat?,
    player: AudioPlayer,
    videoPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenPhoto: () -> Unit,
    onPlayVideo: () -> Unit,
    onVideoEnded: () -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    val r = occurrence.reminder
    val done = occurrence.done
    val color = kindColor(r.kind)
    val cardFill by animateColorAsState(
        when {
            highlighted -> Glass.Lavender.copy(alpha = 0.18f)
            expanded -> Glass.CardStrong
            else -> Glass.Card
        },
        label = "card",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .timelineLine(isFirst, isLast)
            .padding(start = SidePadding, end = SidePadding, bottom = 8.dp),
    ) {
        Text(
            formatTime(occurrence.at),
            modifier = Modifier.width(TimeColumn).padding(top = 6.dp),
            textAlign = TextAlign.End,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (done) Glass.TextFaint else Glass.TextDim,
        )
        Box(Modifier.width(NodeColumn).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .size(NodeSize)
                    .background(Glass.Base, CircleShape) // щоб пунктир не просвічував крізь блідий кружок
                    .background(if (done) color.copy(alpha = 0.28f) else color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) Icons.Default.Check else kindIcon(r.kind), null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        val labelCollapse = stringResource(R.string.chat_collapse)
        val labelExpand = stringResource(R.string.chat_expand)
        val labelActions = stringResource(R.string.chat_actions)
        Column(
            Modifier
                .weight(1f)
                .card(fill = cardFill)
                .combinedClickable(
                    onClickLabel = if (expanded) labelCollapse else labelExpand,
                    onLongClickLabel = labelActions,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Згорнута картка: один рядок — заголовок і значки.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminderTitle(r),
                    color = if (done) Glass.TextDim else Glass.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (done && r.kind == Kind.TEXT) TextDecoration.LineThrough else null,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (r.alarm) MiniIcon(Icons.Default.Alarm, stringResource(R.string.chat_alarm))
                if (r.snoozedUntil != null && !done) MiniIcon(Icons.Default.Snooze, stringResource(R.string.chat_snoozed))
                if (r.repeat != Repeat.NONE) MiniIcon(Icons.Default.Repeat, repeatLabel(r.repeat))
                if (stat != null && stat.count > 0) CommentBadge(stat)
            }
            if (expanded) {
                r.authorName?.let { Text(stringResource(R.string.chat_from_author, it), fontSize = 12.sp, color = Glass.TextFaint) }
                if (hasMoreThanTitle(r)) {
                    Text(r.text, color = Glass.Text, fontSize = 15.sp, lineHeight = 20.sp)
                }
                when (r.kind) {
                    Kind.VOICE -> VoicePlayer(r, player)
                    Kind.VIDEO -> VideoMedia(
                        r,
                        playing = videoPlaying,
                        onPlay = onPlayVideo,
                        onEnded = onVideoEnded,
                        onFullscreen = onOpenPhoto,
                        modifier = Modifier.size(156.dp),
                    )
                    Kind.PHOTO -> AsyncImage(
                        model = r.mediaPath?.let(::File),
                        contentDescription = stringResource(R.string.chat_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Glass.Fill)
                            .clickable(onClick = onOpenPhoto),
                    )
                    Kind.TEXT -> Unit
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) {
                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Glass.TextFaint)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_delivered), fontSize = 12.sp, color = Glass.TextFaint)
                    } else {
                        Text(inLabel(occurrence.at - now), fontSize = 12.sp, color = Glass.Lavender, fontWeight = FontWeight.Medium)
                    }
                    if (r.repeat != Repeat.NONE) {
                        Text(stringResource(R.string.chat_repeat_suffix, repeatLabel(r.repeat).lowercase()), fontSize = 12.sp, color = Glass.TextFaint)
                    }
                }
                expandedContent()
            }
        }
    }
}

@Composable
private fun MiniIcon(icon: ImageVector, description: String) {
    Icon(icon, description, Modifier.padding(start = 6.dp).size(14.dp), tint = Glass.TextFaint)
}

/** «💬 3»; якщо там є нове від інших — підсвічено, з крапкою. */
@Composable
private fun CommentBadge(stat: CommentStat) {
    val unread = stat.unread > 0
    val descUnread = stringResource(R.string.chat_comments_unread, stat.unread)
    val descCount = stringResource(R.string.chat_comments_count, stat.count)
    Row(
        Modifier
            .padding(start = 8.dp)
            .clip(Glass.Pill)
            .background(if (unread) Glass.Lavender.copy(alpha = 0.22f) else Glass.FillStrong)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (unread) descUnread else descCount
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(12.dp), tint = if (unread) Glass.Lavender else Glass.TextDim)
        Spacer(Modifier.width(3.dp))
        Text("${stat.count}", fontSize = 12.sp, color = if (unread) Glass.Lavender else Glass.TextDim, fontWeight = FontWeight.Medium)
        if (unread) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).background(Glass.Lavender, CircleShape))
        }
    }
}

/** Червона лінія «зараз» між минулим і майбутнім. */
@Composable
fun NowLine(now: Long, isFirst: Boolean, isLast: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .timelineLine(isFirst, isLast)
            .padding(start = SidePadding, end = SidePadding, bottom = 8.dp)
            .height(NodeSize + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTime(now),
            modifier = Modifier.width(TimeColumn),
            textAlign = TextAlign.End,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Glass.Danger,
        )
        Box(Modifier.width(NodeColumn), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(Glass.Danger, CircleShape))
        }
        Box(Modifier.weight(1f).height(2.dp).background(Glass.Danger, Glass.Pill))
        Text(stringResource(R.string.chat_now), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Glass.Danger)
    }
}

/** Порожній день. */
@Composable
fun EmptyDay(label: String) {
    Column(
        Modifier
            .padding(horizontal = 44.dp)
            .card(RoundedCornerShape(26.dp))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.chat_empty_day_title, label),
            color = Glass.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.chat_empty_day_subtitle),
            textAlign = TextAlign.Center,
            color = Glass.TextDim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}
