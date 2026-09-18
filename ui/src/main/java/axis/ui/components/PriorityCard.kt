package axis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.TextSecondary

/** Best-available "next up" snapshot. Fully empty → renders nothing. */
data class PriorityData(
    val eventTitle: String? = null,
    val eventCountdown: String? = null,
    val priorityCount: Int = 0,
    val routineChips: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = eventTitle == null && priorityCount == 0 && routineChips.isEmpty()
}

/**
 * Home "NEXT UP" card: next calendar event · priority message count ·
 * armed-routine violet chips. Shows the best-available subset (e.g. no
 * calendar permission → event row simply absent).
 */
@Composable
fun PriorityCard(
    data: PriorityData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (data.isEmpty) return
    GlassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        if (data.eventTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CalendarMonth, contentDescription = null,
                    tint = AccentCyan, modifier = Modifier.size(20.dp)
                )
                Text(
                    text = data.eventTitle,
                    style = AxisType.BodyStrong,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                if (data.eventCountdown != null) {
                    Text(data.eventCountdown, style = AxisType.Caption)
                }
            }
        }
        if (data.priorityCount > 0) {
            if (data.eventTitle != null) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Message, contentDescription = null,
                    tint = AccentCyan, modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (data.priorityCount == 1) "1 priority message"
                    else "${data.priorityCount} priority messages",
                    style = AxisType.Body,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
            }
        }
        if (data.routineChips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bolt, contentDescription = null,
                    tint = AccentViolet, modifier = Modifier.size(20.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    data.routineChips.take(3).forEach { chip ->
                        GlassChip(label = chip, accent = AccentViolet, compact = true)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PriorityCardPreview() {
    AxisTheme {
        PriorityCard(
            PriorityData(
                eventTitle = "Design review",
                eventCountdown = "in 40m",
                priorityCount = 3,
                routineChips = listOf("Good Night", "Driving")
            )
        )
    }
}

@Preview
@Composable
private fun PriorityCardEmptyPreview() {
    // Renders nothing — the section header hides with it (see HomeScreen).
    AxisTheme { PriorityCard(PriorityData()) }
}
