package ua.nahadaika.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Стримане «преміальне» скло в темному режимі: майже чорний фон з глибоким сяйвом,
 * тонке скло з волосяною кромкою, білий — для головних дій, лавандовий — лише для акцентів.
 */
object Glass {
    val Base = Color(0xFF050507)
    val Text = Color(0xFFF5F5F7)
    val TextDim = Color(0x99F5F5F7)
    val TextFaint = Color(0x57F5F5F7)

    val Fill = Color(0x0FFFFFFF)
    val FillStrong = Color(0x1FFFFFFF)
    val FillSubtle = Color(0x08FFFFFF)

    /** Акцент — лише для дрібних деталей (відлік, посилання, активні стани). */
    val Lavender = Color(0xFFB4A8FF)
    val Primary = Color(0xFFF5F5F7)
    val OnPrimary = Color(0xFF0B0B10)
    val Danger = Color(0xFFFF6B6B)
    val Sheet = Color(0xF50B0B10)

    private val GlowIndigo = Color(0xFF3A2E9E)
    private val GlowTeal = Color(0xFF0B5C7A)
    private val GlowPlum = Color(0xFF5B1F6B)
    val glows = listOf(GlowIndigo, GlowTeal, GlowPlum)

    /** Волосяна кромка скла: світліша зверху. */
    val Stroke = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF)))
    val Pill = RoundedCornerShape(percent = 50)

    /** Розмиття того, що під панеллю (Android 12+); на старіших — напівпрозора підкладка. */
    val Haze = HazeStyle(
        backgroundColor = Base,
        tint = HazeTint(Color(0xB3050507)),
        blurRadius = 32.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(Color(0xF0070709)),
    )
}

fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    fill: Brush = SolidColor(Glass.Fill),
    stroke: Brush = Glass.Stroke,
): Modifier = clip(shape).background(fill, shape).border(0.8.dp, stroke, shape)

fun Modifier.glass(shape: Shape, fill: Color): Modifier = glass(shape, SolidColor(fill))

/** Майже чорний фон з глибоким, ледь помітним сяйвом. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Glass.Base)
        val w = size.width
        val h = size.height
        fun glow(color: Color, center: Offset, radius: Float) =
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
        glow(Glass.glows[0].copy(alpha = 0.55f), Offset(w * 0.15f, -h * 0.02f), w * 1.1f)
        glow(Glass.glows[1].copy(alpha = 0.40f), Offset(w * 1.1f, h * 0.55f), w * 0.95f)
        glow(Glass.glows[2].copy(alpha = 0.35f), Offset(-w * 0.1f, h * 1.02f), w * 1.0f)
    }
}

/** Ледь помітне сяйво всередині нижніх вікон. */
fun Modifier.sheetGlow(): Modifier = drawBehind {
    val w = size.width
    drawCircle(
        Brush.radialGradient(listOf(Glass.glows[0].copy(alpha = 0.35f), Color.Transparent), Offset(w * 0.2f, 0f), w * 0.9f),
        w * 0.9f,
        Offset(w * 0.2f, 0f),
    )
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(18.dp),
        containerColor = Glass.Sheet,
        contentColor = Glass.Text,
        actionColor = Glass.Lavender,
        dismissActionContentColor = Glass.TextDim,
        modifier = Modifier.padding(horizontal = 12.dp).border(0.8.dp, Glass.Stroke, RoundedCornerShape(18.dp)),
    )
}

/** Перемикач як в iOS: темна доріжка, світлий скляний «повзунок». */
@Composable
fun GlassSegmented(
    options: List<Pair<ImageVector?, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.clip(Glass.Pill).background(Glass.Fill).padding(3.dp)) {
        options.forEachIndexed { i, (icon, label) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(Glass.Pill)
                    .then(if (active) Modifier.background(Glass.FillStrong).border(0.8.dp, Glass.Stroke, Glass.Pill) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(16.dp), tint = if (active) Glass.Text else Glass.TextFaint)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    label,
                    color = if (active) Glass.Text else Glass.TextDim,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Головна кнопка: світла «пігулка» з темним текстом. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(54.dp)
            .clip(Glass.Pill)
            .background(if (enabled) Glass.Primary else Glass.Fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Glass.OnPrimary else Glass.TextFaint,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Скляна «пігулка» (швидкі варіанти, повтор). */
@Composable
fun GlassChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) {
    Row(
        modifier
            .clip(Glass.Pill)
            .background(if (selected) Glass.FillStrong else Glass.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, Modifier.size(16.dp), tint = Glass.TextDim)
            Spacer(Modifier.width(7.dp))
        }
        Text(label, color = Glass.Text, fontSize = 14.sp, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, null, Modifier.size(18.dp), tint = Glass.TextFaint)
        }
    }
}

/** Кругла скляна кнопка з іконкою. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Glass.Text,
) {
    Box(
        modifier.size(size).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Кругла світла кнопка головної дії (запис, запланувати, новий чат). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrimaryCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Glass.Primary)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Glass.OnPrimary) { content() }
    }
}
