package ua.nahadaika.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF2AABEE)

private val Light = lightColorScheme(
    primary = Color(0xFF1C8ADB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFFDDE),
    onPrimaryContainer = Color(0xFF1B2A12),
    secondaryContainer = Color(0xFFD7ECFB),
    tertiaryContainer = Color(0xFFFFF1B8),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFE6EAEE),
    onSurfaceVariant = Color(0xFF5F6B76),
    outline = Color(0xFF8A96A0),
    outlineVariant = Color(0xFFD5DBE0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF3F7),
    surfaceContainer = Color(0xFFF1F4F7),
    surfaceContainerHigh = Color(0xFFECEFF3),
    surfaceContainerHighest = Color(0xFFE6EAEE),
)

private val Dark = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B5278),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF1F3A52),
    tertiaryContainer = Color(0xFF5A4A1A),
    background = Color(0xFF17212B),
    surface = Color(0xFF17212B),
    surfaceVariant = Color(0xFF2B3746),
    onSurfaceVariant = Color(0xFF8B9BAA),
    outline = Color(0xFF6D7F8F),
    outlineVariant = Color(0xFF2B3746),
    surfaceContainerLowest = Color(0xFF0E1621),
    surfaceContainerLow = Color(0xFF0E1621),
    surfaceContainer = Color(0xFF1C2733),
    surfaceContainerHigh = Color(0xFF232E3C),
    surfaceContainerHighest = Color(0xFF2B3746),
)

@Composable
fun NahadaikaTheme(content: @Composable () -> Unit) {
    // Власна палітра у стилі Telegram замість системних Material You кольорів — однаково на всіх телефонах.
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
