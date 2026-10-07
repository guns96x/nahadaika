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
import androidx.compose.ui.res.stringResource
import ua.nahadaika.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ua.nahadaika.localDateFormatter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import ua.nahadaika.data.Chat
import ua.nahadaika.ui.theme.Glass
import ua.nahadaika.ui.theme.GlassIconButton
import ua.nahadaika.ui.theme.edgeFade
import ua.nahadaika.ui.theme.glassHaze
import java.time.LocalDate

internal val HeaderHeight = 48.dp

/** Кнопки праворуч трохи менші, щоб капсула з назвою чату лишалась широкою. */
private val SideButton = 44.dp

/** Окремі скляні капсули однакової висоти, як у Telegram: ☰ | чат | пошук — і смужка днів під ними (тема — у списку чатів). */
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
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Default.Menu, stringResource(R.string.chat_all_chats), onClick = onOpenChats, size = HeaderHeight, haze = haze)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(HeaderHeight)
                    .glassHaze(haze)
                    .clickable(onClick = onOpenChats)
                    .padding(start = 5.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chat?.let { c ->
                    Avatar(c, size = 38)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name,
                            color = Glass.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            nextIn ?: stringResource(R.string.chat_no_scheduled),
                            color = Glass.TextDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Місяць обраного дня (коротко, щоб не тіснити підпис); якщо обрано не сьогодні — під ним швидкий повернення до сьогодні.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        selectedDate.format(if (selectedDate.year == today.year) localDateFormatter("LLL", "LLL") else localDateFormatter("LLL yyyy", "LLLy")).replaceFirstChar { it.uppercase() },
                        color = Glass.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    if (selectedDate != today) {
                        Text(
                            stringResource(R.string.chat_today),
                            color = Glass.Lavender,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(Glass.Pill).clickable { onSelectDate(today) }.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Default.Search, stringResource(R.string.chat_search), onClick = onOpenSearch, size = SideButton, haze = haze)
        }
        DayStrip(
            selected = selectedDate,
            today = today,
            dotsFor = dotsFor,
            onSelect = onSelectDate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
