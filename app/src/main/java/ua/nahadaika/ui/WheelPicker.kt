package ua.nahadaika.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import ua.nahadaika.ui.theme.Glass
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

private const val LOOPS = 1000
const val WHEEL_VISIBLE = 5
val WheelItemHeight = 46.dp

/**
 * Барабан, як у таймері Samsung чи «Надіслати пізніше» в Telegram.
 * [loop] — крутиться по колу (після 59 знову 00); без нього — звичайний список (дні).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    fontSize: TextUnit = 28.sp,
    itemHeight: Dp = WheelItemHeight,
    label: (Int) -> String = { "%02d".format(it) },
) {
    val total = if (loop) count * LOOPS else count
    val base = if (loop) total / 2 - (total / 2) % count else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + value.coerceIn(0, count - 1))
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
                ?: state.firstVisibleItemIndex
        }
    }
    val haptics = LocalHapticFeedback.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    var programmatic by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        snapshotFlow { center % count }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                if (!programmatic) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnChange(it)
                }
            }
    }

    // Значення змінили ззовні (швидкі кнопки, інша вкладка) — докручуємо найкоротшим шляхом.
    LaunchedEffect(value) {
        val current = center % count
        if (current == value || state.isScrollInProgress) return@LaunchedEffect
        val target = if (loop) {
            var diff = (value - current + count) % count
            if (diff > count / 2) diff -= count
            center + diff
        } else {
            value.coerceIn(0, count - 1)
        }
        programmatic = true
        try {
            state.animateScrollToItem(target)
        } finally {
            programmatic = false
        }
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = itemHeight * (WHEEL_VISIBLE / 2)),
        modifier = modifier.height(itemHeight * WHEEL_VISIBLE),
    ) {
        items(total) { index ->
            val distance = abs(index - center)
            Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                Text(
                    label(index % count),
                    fontSize = fontSize,
                    fontWeight = if (distance == 0) FontWeight.Normal else FontWeight.Light,
                    color = Glass.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.alpha(
                        when (distance) {
                            0 -> 1f
                            1 -> 0.38f
                            else -> 0.12f
                        },
                    ),
                )
            }
        }
    }
}

/** Скляна смуга вибору по центру барабанів. */
fun Modifier.wheelSelectionBand(): Modifier = drawBehind {
    val h = WheelItemHeight.toPx()
    val top = (size.height - h) / 2
    val radius = CornerRadius(h / 2.6f)
    drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(0f, top), Size(size.width, h), radius)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)), startY = top, endY = top + h),
        Offset(0f, top),
        Size(size.width, h),
        radius,
        style = Stroke(0.8.dp.toPx()),
    )
}
