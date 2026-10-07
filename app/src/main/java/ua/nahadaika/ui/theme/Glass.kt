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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint

/** Кольори «скла» для однієї теми. */
@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val base: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val fill: Color,
    val fillStrong: Color,
    val fillSubtle: Color,
    val accent: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val sheet: Color,
    val scrim: Color,
    val glows: List<Pair<Color, Float>>,
    val stroke: Brush,
    val hazeTint: Color,
    val hazeFallback: Color,
    /** Тонування окремих скляних капсул (шапка, поле вводу). */
    val capsuleTint: Color,
    val capsuleFallback: Color,
)

/** Темна: майже чорний фон з глибоким сяйвом, тонке скло, світлі кнопки. */
val DarkGlass = GlassPalette(
    isDark = true,
    base = Color(0xFF050507),
    text = Color(0xFFF5F5F7),
    textDim = Color(0x99F5F5F7),
    textFaint = Color(0x57F5F5F7),
    fill = Color(0x0FFFFFFF),
    fillStrong = Color(0x1FFFFFFF),
    fillSubtle = Color(0x08FFFFFF),
    accent = Color(0xFFB4A8FF),
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF0B0B10),
    danger = Color(0xFFFF6B6B),
    sheet = Color(0xF50B0B10),
    scrim = Color(0x8C000000),
    glows = listOf(Color(0xFF3A2E9E) to 0.55f, Color(0xFF0B5C7A) to 0.40f, Color(0xFF5B1F6B) to 0.35f),
    stroke = Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF))),
    hazeTint = Color(0xB3050507),
    hazeFallback = Color(0xF0070709),
    capsuleTint = Color(0x9E16161C),
    capsuleFallback = Color(0xF016161C),
)

/** Світла: світлий фон з пастельним сяйвом, біле матове скло, темні кнопки. */
val LightGlass = GlassPalette(
    isDark = false,
    base = Color(0xFFEFF0F5),
    text = Color(0xFF101015),
    textDim = Color(0x99101015),
    textFaint = Color(0x5C101015),
    fill = Color(0x99FFFFFF),
    fillStrong = Color(0xE6FFFFFF),
    fillSubtle = Color(0x59FFFFFF),
    accent = Color(0xFF5B4BDB),
    primary = Color(0xFF111118),
    onPrimary = Color(0xFFF7F7FA),
    danger = Color(0xFFE5484D),
    sheet = Color(0xF5F6F6FA),
    scrim = Color(0x40000000),
    glows = listOf(Color(0xFFC6BDFF) to 0.75f, Color(0xFFB7E3F8) to 0.65f, Color(0xFFF6C8E1) to 0.55f),
    stroke = Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0x0F000000))),
    hazeTint = Color(0x9EEFF0F5),
    hazeFallback = Color(0xEBEFF0F5),
    capsuleTint = Color(0xB8FFFFFF),
    capsuleFallback = Color(0xF5FFFFFF),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/**
 * Токени поточної теми. Стримане «преміальне» скло: тонкі панелі з волосяною кромкою,
 * головні дії — контрастні кнопки, акцентний колір — лише для дрібних деталей.
 */
object Glass {
    val palette: GlassPalette @Composable @ReadOnlyComposable get() = LocalGlass.current
    val Base: Color @Composable @ReadOnlyComposable get() = palette.base
    val Text: Color @Composable @ReadOnlyComposable get() = palette.text
    val TextDim: Color @Composable @ReadOnlyComposable get() = palette.textDim
    val TextFaint: Color @Composable @ReadOnlyComposable get() = palette.textFaint
    val Fill: Color @Composable @ReadOnlyComposable get() = palette.fill
    val FillStrong: Color @Composable @ReadOnlyComposable get() = palette.fillStrong
    val FillSubtle: Color @Composable @ReadOnlyComposable get() = palette.fillSubtle
    val Lavender: Color @Composable @ReadOnlyComposable get() = palette.accent
    val Primary: Color @Composable @ReadOnlyComposable get() = palette.primary
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = palette.onPrimary
    val Danger: Color @Composable @ReadOnlyComposable get() = palette.danger
    val Sheet: Color @Composable @ReadOnlyComposable get() = palette.sheet
    val Scrim: Color @Composable @ReadOnlyComposable get() = palette.scrim
    val Stroke: Brush @Composable @ReadOnlyComposable get() = palette.stroke
    val Pill = RoundedCornerShape(percent = 50)

    /** Розмиття того, що під панеллю (Android 12+); на старіших — напівпрозора підкладка. */
    val Haze: HazeStyle
        @Composable @ReadOnlyComposable get() = HazeStyle(
            backgroundColor = palette.base,
            tint = HazeTint(palette.hazeTint),
            blurRadius = 32.dp,
            noiseFactor = if (palette.isDark) 0.04f else 0.02f,
            fallbackTint = HazeTint(palette.hazeFallback),
        )
}

@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(22.dp),
    fill: Brush = SolidColor(Glass.Fill),
    stroke: Brush = Glass.Stroke,
): Modifier = clip(shape).background(fill, shape).border(0.8.dp, stroke, shape)

@Composable
fun Modifier.glass(shape: Shape, fill: Color): Modifier = glass(shape, SolidColor(fill))

/**
 * Скляна капсула, як у Telegram: розмиває лише те, що під нею (Android 12+),
 * з тонкою світлою кромкою. Без [state] — звичайне напівпрозоре скло.
 */
@Composable
fun Modifier.glassHaze(state: HazeState?, shape: Shape = Glass.Pill): Modifier {
    if (state == null) return glass(shape)
    val p = Glass.palette
    return clip(shape)
        .hazeEffect(
            state,
            HazeStyle(
                backgroundColor = p.base,
                tint = HazeTint(p.capsuleTint),
                blurRadius = 24.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(p.capsuleFallback),
            ),
        )
        .border(0.8.dp, p.stroke, shape)
}

/**
 * Крапля «рідкого скла» (як лінза в iOS): напівпрозоре тіло, відблиск угорі, світла кромка й м'яка тінь.
 * Малюється під текстом, тож вміст лишається чітким, а крапля ніби збільшує його.
 */
@Composable
fun Modifier.liquidLens(shape: Shape = RoundedCornerShape(22.dp)): Modifier {
    val dark = Glass.palette.isDark
    val body = if (dark) {
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))
    } else {
        listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.55f))
    }
    val rim = if (dark) {
        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.3f))
    } else {
        // На світлому склі біла крапля губиться — знизу кромка трохи темніша.
        listOf(Color.White, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.14f))
    }
    val gloss = Color.White.copy(alpha = if (dark) 0.14f else 0.5f)
    return shadow(if (dark) 12.dp else 14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
        .clip(shape)
        .background(Brush.verticalGradient(body))
        .drawWithContent {
            drawContent()
            // Відблиск: світло зверху плавно гасне до середини краплі.
            drawRect(Brush.verticalGradient(listOf(gloss, Color.Transparent), endY = size.height * 0.5f))
        }
        .border(1.dp, Brush.verticalGradient(rim), shape)
}

/** М'яке затемнення від краю екрана — замість суцільних смуг під шапкою та полем вводу. */
@Composable
fun Modifier.edgeFade(top: Boolean): Modifier {
    val base = Glass.Base
    val colors = listOf(base.copy(alpha = 0.92f), base.copy(alpha = 0.6f), base.copy(alpha = 0f))
    return background(Brush.verticalGradient(if (top) colors else colors.reversed()))
}

/** Фон з м'яким сяйвом — під ним видно, що панелі скляні. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val p = Glass.palette
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.base)
        val w = size.width
        val h = size.height
        fun glow(i: Int, center: Offset, radius: Float) {
            val (color, alpha) = p.glows[i]
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius), radius, center)
        }
        glow(0, Offset(w * 0.15f, -h * 0.02f), w * 1.1f)
        glow(1, Offset(w * 1.1f, h * 0.55f), w * 0.95f)
        glow(2, Offset(-w * 0.1f, h * 1.02f), w * 1.0f)
    }
}

/** Ледь помітне сяйво всередині нижніх вікон. */
@Composable
fun Modifier.sheetGlow(): Modifier {
    val (color, alpha) = Glass.palette.glows[0]
    return drawBehind {
        val w = size.width
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.6f), Color.Transparent), Offset(w * 0.2f, 0f), w * 0.9f),
            w * 0.9f,
            Offset(w * 0.2f, 0f),
        )
    }
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
    haze: HazeState? = null,
) {
    val track = if (haze != null) Modifier.glassHaze(haze) else Modifier.clip(Glass.Pill).background(Glass.Fill)
    Row(modifier.then(track).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, (icon, label) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(Glass.Pill)
                    .then(if (active) Modifier.background(Glass.FillStrong).border(0.8.dp, Glass.Stroke, Glass.Pill) else Modifier)
                    .clickable { onSelect(i) }
                    .fillMaxHeight(),
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
    haze: HazeState? = null,
) {
    Box(
        modifier.size(size).glassHaze(haze, CircleShape).clickable(onClick = onClick),
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
