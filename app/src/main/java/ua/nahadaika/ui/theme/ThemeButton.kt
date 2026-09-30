package ua.nahadaika.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.AUTO -> Icons.Default.BrightnessAuto
    ThemeMode.DARK -> Icons.Default.DarkMode
    ThemeMode.LIGHT -> Icons.Default.LightMode
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.AUTO -> "Авто (як у системі)"
    ThemeMode.DARK -> "Темна"
    ThemeMode.LIGHT -> "Світла"
}

/** Кнопка вибору теми: темна, світла або як у системі. */
@Composable
fun ThemeModeButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GlassIconButton(ThemeSettings.mode.icon(), "Тема", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO).forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    leadingIcon = { Icon(mode.icon(), null) },
                    trailingIcon = { if (mode == ThemeSettings.mode) Icon(Icons.Default.Check, null, tint = Glass.Lavender) },
                    onClick = {
                        ThemeSettings.set(context, mode)
                        open = false
                    },
                )
            }
        }
    }
}
