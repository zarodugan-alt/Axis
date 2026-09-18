package axis.app.sidecar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.app.nav.Routes
import axis.ui.components.AxisOrb
import axis.ui.components.GlassChip
import axis.ui.components.OrbState
import axis.ui.components.StatusDot
import axis.ui.components.TelemetryReadout
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.BgElevated
import axis.ui.theme.TextSecondary
import axis.ui.theme.TrackFill

/**
 * Side-Car panel (spec §S4): systems · active tasks · telemetry · mode chips.
 * Slides from the right edge; content parallaxes against the panel offset
 * ([shiftPx] — header 1.0x, cards 0.85x, telemetry 0.7x). Dismiss: ✕, scrim,
 * back, or swipe-right.
 */
@Composable
fun SideCarPanel(
    shiftPx: Float,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: SideCarViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val haptics by vm.hapticsEnabled.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shape = remember { RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) }

    fun fireFix(fix: SystemFix) {
        AxisHaptics.press(view, haptics)
        when (fix) {
            is SystemFix.Route -> onNavigate(fix.route)
            is SystemFix.SettingsAction -> {
                val intent = Intent(
                    fix.action,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .shadow(16.dp, shape)
            .clip(shape)
            .background(BgElevated)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(onClose) {
                awaitEachGesture {
                    awaitFirstDown()
                    var acc = 0f
                    val threshold = with(density) { 80.dp.toPx() }
                    var done = false
                    while (!done) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            done = true
                        } else {
                            acc += change.position.x - change.previousPosition.x
                            if (acc > threshold) {
                                onClose()
                                done = true
                            }
                        }
                    }
                }
            }
            .padding(horizontal = AxisSpacing.screen, vertical = 12.dp)
    ) {
        // Header — parallax 1.0x (rides with the panel).
        Row(verticalAlignment = Alignment.CenterVertically) {
            AxisOrb(OrbState.IDLE, size = 28.dp)
            Text(
                "AXIS CORE",
                style = AxisType.Section,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onNavigate(Routes.SETTINGS) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close panel", tint = TextSecondary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Systems + tasks — parallax 0.85x.
            Column(
                modifier = Modifier.graphicsLayer {
                    translationX = -shiftPx * (1f - 0.85f)
                }
            ) {
                Spacer(Modifier.height(8.dp))
                Text("SYSTEMS", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                state.systems.forEach { row ->
                    SystemRowView(row = row, onFix = ::fireFix)
                }
                Spacer(Modifier.height(AxisSpacing.section))
                Text("ACTIVE TASKS", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                if (state.tasks.isEmpty()) {
                    Text("No tasks running", style = AxisType.Caption)
                    Text("Automations arrive in Phase 3", style = AxisType.Caption)
                } else {
                    state.tasks.forEach { TaskRowView(task = it) }
                }
            }

            // Telemetry + modes — parallax 0.7x.
            Column(
                modifier = Modifier.graphicsLayer {
                    translationX = -shiftPx * (1f - 0.7f)
                }
            ) {
                Spacer(Modifier.height(AxisSpacing.section))
                Text("TELEMETRY", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                TelemetryReadout(values = state.telemetry)
                Text(
                    "History sparkline arrives in Phase 2",
                    style = AxisType.Caption,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip("Focus", "focus", state.mode, haptics) { vm.setMode(it) }
                    ModeChip("Sleep", "sleep", state.mode, haptics) { vm.setMode(it) }
                    ModeChip("Drive", "drive", state.mode, haptics) { vm.setMode(it) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SystemRowView(row: SystemRow, onFix: (SystemFix) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (row.fix != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onFix(row.fix) }
                    )
                } else Modifier
            )
    ) {
        StatusDot(row.status)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(row.label, style = AxisType.BodyStrong)
            Text(row.detail, style = AxisType.Caption)
        }
        Spacer(Modifier.weight(1f))
        if (row.fix != null) {
            Text(
                "Fix",
                style = AxisType.Button,
                color = AccentCyan,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun TaskRowView(task: TaskRow) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(task.title, style = AxisType.BodyStrong)
        Text(task.stepLabel, style = AxisType.Caption)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(TrackFill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(task.progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(AccentCyan)
            )
        }
    }
}

@Composable
private fun RowScope.ModeChip(
    label: String,
    mode: String,
    activeMode: String,
    haptics: Boolean,
    onSelect: (String) -> Unit
) {
    val view = LocalView.current
    GlassChip(
        label = label,
        active = activeMode == mode,
        accent = AccentViolet,
        modifier = Modifier.weight(1f),
        onClick = {
            AxisHaptics.press(view, haptics)
            onSelect(if (activeMode == mode) "off" else mode)
        }
    )
}
