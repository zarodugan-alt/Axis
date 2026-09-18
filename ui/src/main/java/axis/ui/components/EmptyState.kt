package axis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType

/**
 * Honest placeholder for P2+ destinations: names the phase so testers know
 * what's coming instead of guessing what's broken.
 */
@Composable
fun ComingSoon(
    phase: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AxisSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AxisOrb(OrbState.IDLE, orbSize = 72.dp)
        Spacer(Modifier.height(16.dp))
        GlassChip(label = phase, icon = Icons.Rounded.AutoAwesome, compact = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = AxisType.Title, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = AxisType.Body,
            color = AccentCyan.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun ComingSoonPreview() {
    AxisTheme {
        ComingSoon(
            phase = "Phase 3",
            title = "Chat",
            description = "The AI brain arrives in P3 — the launcher works fully until then."
        )
    }
}
