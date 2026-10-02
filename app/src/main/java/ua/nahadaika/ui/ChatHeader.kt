package ua.nahadaika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import ua.nahadaika.data.Chat
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.ThemeModeButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glassHaze
import java.time.LocalDate

internal val HeaderHeight = 56.dp

/** Кнопки праворуч трохи менші, щоб капсула з назвою чату лишалась широкою. */
private val SideButton = 48.dp

/** Окремі скляні капсули однакової висоти, як у Telegram: ☰ | чат | пошук | тема — і смужка днів під ними. */
@Composable
internal fun ChatHeader(
    chat: Chat?,
    nextIn: String?,
    selectedDate: LocalDate,
    today: LocalDate,
    dotsFor: (LocalDate) -> List<Color>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenChats: () -> Unit,
    onOpenSearch: () -> Unit,
    haze: HazeState,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .edgeFade(top = true)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Default.Menu, "Усі чати", onClick = onOpenChats, size = HeaderHeight, haze = haze)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(HeaderHeight)
                    .glassHaze(haze)
                    .clickable(onClick = onOpenChats)
                    .padding(start = 6.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chat?.let { c ->
                    Avatar(c, size = 44)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            c.name,
                            color = Glass.Text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            nextIn ?: "немає запланованих",
                            color = Glass.TextDim,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Default.Search, "Пошук", onClick = onOpenSearch, size = SideButton, haze = haze)
            Spacer(Modifier.width(6.dp))
            ThemeModeButton(size = SideButton, haze = haze)
        }
        DayStrip(
            selected = selectedDate,
            today = today,
            dotsFor = dotsFor,
            onSelect = onSelectDate,
            haze = haze,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
