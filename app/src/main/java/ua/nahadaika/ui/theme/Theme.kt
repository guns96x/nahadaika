package ua.nahadaika.ui.theme

import androidx.compose.material3.MaterialTheme
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

// Завжди темна тема: стримане скло на майже чорному фоні.
private val GlassScheme = darkColorScheme(
    primary = Glass.Lavender,
    onPrimary = Glass.OnPrimary,
    primaryContainer = Glass.FillStrong,
    onPrimaryContainer = Glass.Text,
    secondary = Glass.Lavender,
    onSecondary = Glass.OnPrimary,
    secondaryContainer = Glass.FillStrong,
    onSecondaryContainer = Glass.Text,
    tertiaryContainer = Glass.FillStrong,
    onTertiaryContainer = Glass.Text,
    background = Glass.Base,
    onBackground = Glass.Text,
    surface = Glass.Base,
    onSurface = Glass.Text,
    surfaceVariant = Glass.Fill,
    onSurfaceVariant = Glass.TextDim,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF09090D),
    surfaceContainerLow = Color(0xFF0E0E13),
    surfaceContainer = Color(0xFF141419),
    surfaceContainerHigh = Color(0xFF1A1A20),
    surfaceContainerHighest = Color(0xFF212128),
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x17FFFFFF),
    inverseSurface = Color(0xFF1C1C22),
    inverseOnSurface = Glass.Text,
    inversePrimary = Glass.Lavender,
    error = Glass.Danger,
    onError = Color.White,
    scrim = Color.Black,
)

@Composable
fun NahadaikaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlassScheme, typography = Typography().withInter(), content = content)
}
