package ua.nahadaika.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ua.nahadaika.Prefs
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ua.nahadaika.R

/** Inter — чистий сучасний шрифт з кирилицею та табличними цифрами (для відліку). */
val Inter = FontFamily(
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun Typography.withInter(): Typography {
    fun TextStyle.i() = copy(fontFamily = Inter)
    return copy(
        displayLarge = displayLarge.i(), displayMedium = displayMedium.i(), displaySmall = displaySmall.i(),
        headlineLarge = headlineLarge.i(), headlineMedium = headlineMedium.i(), headlineSmall = headlineSmall.i(),
        titleLarge = titleLarge.i(), titleMedium = titleMedium.i(), titleSmall = titleSmall.i(),
        bodyLarge = bodyLarge.i(), bodyMedium = bodyMedium.i(), bodySmall = bodySmall.i(),
        labelLarge = labelLarge.i(), labelMedium = labelMedium.i(), labelSmall = labelSmall.i(),
    )
}

/** Вибір теми: як у системі, завжди темна або завжди світла. */
enum class ThemeMode { AUTO, DARK, LIGHT }

object ThemeSettings {
    var mode by mutableStateOf(ThemeMode.LIGHT)
        private set

    fun init(context: Context) {
        mode = runCatching { ThemeMode.valueOf(Prefs.themeMode(context)) }.getOrDefault(ThemeMode.LIGHT)
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        Prefs.setThemeMode(context, value.name)
    }
}

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

private fun colorScheme(p: GlassPalette): ColorScheme {
    val containers = if (p.isDark) {
        listOf(0xFF09090D, 0xFF0E0E13, 0xFF141419, 0xFF1A1A20, 0xFF212128)
    } else {
        listOf(0xFFFFFFFF, 0xFFF7F7FA, 0xFFF2F2F6, 0xFFECECF1, 0xFFE6E6EC)
    }.map { Color(it) }
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = if (p.isDark) p.onPrimary else Color.White,
        primaryContainer = p.fillStrong,
        onPrimaryContainer = p.text,
        secondary = p.accent,
        onSecondary = if (p.isDark) p.onPrimary else Color.White,
        secondaryContainer = p.fillStrong,
        onSecondaryContainer = p.text,
        tertiaryContainer = p.fillStrong,
        onTertiaryContainer = p.text,
        background = p.base,
        onBackground = p.text,
        surface = p.base,
        onSurface = p.text,
        surfaceVariant = p.fill,
        onSurfaceVariant = p.textDim,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = containers[0],
        surfaceContainerLow = containers[1],
        surfaceContainer = containers[2],
        surfaceContainerHigh = containers[3],
        surfaceContainerHighest = containers[4],
        outline = p.textFaint,
        outlineVariant = p.fillStrong,
        inverseSurface = if (p.isDark) Color(0xFF1C1C22) else Color(0xFF1C1C22),
        inverseOnSurface = Color(0xFFF5F5F7),
        inversePrimary = p.accent,
        error = p.danger,
        onError = Color.White,
        scrim = Color.Black,
    )
}

@Composable
fun NahadaikaTheme(content: @Composable () -> Unit) {
    val palette = if (isDarkTheme()) DarkGlass else LightGlass
    CompositionLocalProvider(LocalGlass provides palette) {
        MaterialTheme(colorScheme = colorScheme(palette), typography = Typography().withInter(), content = content)
    }
}
