package axis.app.home

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import axis.app.nav.Routes
import axis.kernel.model.AppEntry
import axis.ui.components.AppIcon
import axis.ui.components.DotStatus
import axis.ui.components.Sparkline
import axis.ui.components.TelemetryReadout
import axis.ui.components.TelemetryValue
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning
import axis.ui.theme.glass

/**
 * The six subsystem modules. Each one is a real capability of the device with
 * a real state — never a decorative tile.
 */
@Composable
fun ModuleGrid(
    state: HomeState,
    onNavigate: (String) -> Unit,
    haptics: () -> Unit
) {
    val s = state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircuitModule(
                label = "AI CORE",
                value = if (s.providersConnected == 0) "OFFLINE" else "${s.providersConnected} LIVE",
                caption = if (s.providersConnected == 0) "no key wired" else "BYOK gateway",
                glyph = ChipGlyph.CORE,
                status = if (s.providersConnected > 0) DotStatus.ON else DotStatus.OFF,
                accent = if (s.providersConnected > 0) AccentCyan else TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(if (s.providersConnected == 0) Routes.SETTINGS_PROVIDERS else Routes.CHAT)
                }
            )
            CircuitModule(
                label = "SENSE",
                value = "${s.unread} HELD",
                caption = if (s.notificationAccess) "listener wired" else "access needed",
                glyph = ChipGlyph.SENSE,
                status = when {
                    !s.notificationAccess -> DotStatus.OFF
                    s.unread > 0 -> DotStatus.DEGRADED
                    else -> DotStatus.ON
                },
                accent = if (s.notificationAccess) AccentCyan else Warning,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(Routes.NOTIFICATIONS)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircuitModule(
                label = "LOGIC",
                value = "${s.routinesEnabled} ARMED",
                caption = s.lastRoutine?.let { "last: $it" } ?: "no routines armed",
                glyph = ChipGlyph.LOGIC,
                status = if (s.routinesEnabled > 0) DotStatus.ON else DotStatus.OFF,
                accent = AccentViolet,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(Routes.ROUTINES)
                }
            )
            CircuitModule(
                label = "SAFE",
                value = if (s.killSwitch) "STOPPED" else "ARMED",
                caption = if (s.killSwitch) "kill switch engaged" else "gates active",
                glyph = ChipGlyph.SAFE,
                status = if (s.killSwitch) DotStatus.DEGRADED else DotStatus.ON,
                accent = if (s.killSwitch) Danger else Success,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(Routes.SETTINGS_SAFETY)
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircuitModule(
                label = "VOICE",
                value = if (s.speechReady) "CLOUD" else "ON-DEVICE",
                caption = "speech out",
                glyph = ChipGlyph.VOICE,
                status = DotStatus.ON,
                accent = AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(Routes.SETTINGS_VOICE)
                }
            )
            CircuitModule(
                label = "VAULT",
                value = "${s.providersConnected} KEYS",
                caption = "AES-GCM sealed",
                glyph = ChipGlyph.VAULT,
                status = if (s.providersConnected > 0) DotStatus.ON else DotStatus.OFF,
                accent = AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics()
                    onNavigate(Routes.SETTINGS_PROVIDERS)
                }
            )
        }
    }
}

/** Physical-feeling tool switches with live state. */
@Composable
fun ToolRail(
    state: HomeState,
    onTorch: (Boolean) -> Unit,
    onDnd: (Boolean) -> Unit,
    onRotation: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onSettings: (String) -> Unit,
    onKillSwitch: (Boolean) -> Unit,
    haptics: () -> Unit
) {
    val s = state.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                label = "TORCH",
                stateLabel = if (s.torchOn) "ON" else "OFF",
                on = s.torchOn,
                glyph = ChipGlyph.TOOL,
                enabled = state.flashAvailable,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onTorch(!s.torchOn) }
            )
            ToolTile(
                label = "DND",
                stateLabel = if (s.dndOn) "ON" else "OFF",
                on = s.dndOn,
                glyph = ChipGlyph.SAFE,
                warn = s.dndOn,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onDnd(!s.dndOn) }
            )
            ToolTile(
                label = "ROTATE",
                stateLabel = if (s.rotationLocked) "LOCK" else "AUTO",
                on = s.rotationLocked,
                glyph = ChipGlyph.LOGIC,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onRotation(!s.rotationLocked) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                label = "DIM",
                stateLabel = "${s.brightness * 100 / 255}%",
                on = false,
                glyph = ChipGlyph.WAVE,
                enabled = state.storageWrites,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onBrightness(-15) }
            )
            ToolTile(
                label = "BRIGHT",
                stateLabel = "${s.brightness * 100 / 255}%",
                on = false,
                glyph = ChipGlyph.WAVE,
                enabled = state.storageWrites,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onBrightness(15) }
            )
            ToolTile(
                label = "WI-FI",
                stateLabel = if (s.wifi) "UP" else "OFF",
                on = s.wifi,
                glyph = ChipGlyph.SENSE,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onSettings("wifi") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                label = if (state.killSwitch) "RESUME" else "KILL SWITCH",
                stateLabel = if (state.killSwitch) "STOPPED" else "ARMED",
                on = state.killSwitch,
                warn = state.killSwitch,
                glyph = ChipGlyph.SAFE,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onKillSwitch(!state.killSwitch) }
            )
            ToolTile(
                label = "SETTINGS",
                stateLabel = "OPEN",
                on = false,
                glyph = ChipGlyph.TOOL,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onSettings("settings") }
            )
            ToolTile(
                label = "SERVICES",
                stateLabel = if (state.screenAccess && state.notificationAccess) "OK" else "LIMITED",
                on = state.screenAccess && state.notificationAccess,
                warn = !(state.screenAccess && state.notificationAccess),
                glyph = ChipGlyph.CORE,
                modifier = Modifier.weight(1f),
                onClick = { haptics(); onSettings("accessibility") }
            )
        }
    }
}

/**
 * The terminal: a command line for apps and for AXIS. Typing filters apps
 * (mono rows, no icons on the board surface); Enter asks the agent; a final
 * row falls back to a web search.
 */
@Composable
fun Terminal(
    query: String,
    results: List<AppEntry>,
    onQueryChange: (String) -> Unit,
    iconFor: (String) -> Drawable?,
    onLaunch: (String) -> Unit,
    onAsk: (String) -> Unit,
    onWeb: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    haptics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(corner = 16.dp)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("root@axis:~$", style = AxisType.Telemetry.copy(color = Success))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("search apps, ask ax…", style = AxisType.Telemetry.copy(color = TextSecondary))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = AxisType.Telemetry.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AccentCyan),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            results.take(6).forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onLaunch(app.packageName) }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(icon = iconFor(app.packageName), label = app.label, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        app.label,
                        style = AxisType.Telemetry.copy(color = TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (app.isNew) {
                        Text("new", style = AxisType.Caption.copy(color = AccentCyan))
                    }
                }
            }
            if (results.isEmpty()) {
                Text(
                    "no app matches — press ▸ to ask AXIS",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalAction("▸ ask ax: \"${query.take(24)}\"", AccentCyan) { onAsk(query) }
                TerminalAction("⌕ web", AccentViolet) { onWeb(query) }
            }
        }
    }
}

@Composable
private fun TerminalAction(label: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .glass(corner = 50.dp, borderColor = accent.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = AxisType.Caption.copy(color = accent))
    }
}

/** Live device telemetry: load sparkline plus labelled mono values. */
@Composable
fun TelemetryPanel(state: HomeState) {
    val s = state.snapshot
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(corner = 16.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CPU LOAD", style = AxisType.Caption)
            Text(
                "${(s.cpuLoad * 100).toInt()}% · ${s.cpuCores} cores",
                style = AxisType.Telemetry.copy(
                    color = if (s.cpuLoad > 0.8f) Warning else TextPrimary
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(
            data = if (state.loadHistory.isEmpty()) listOf(0f) else state.loadHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
        Spacer(Modifier.height(10.dp))
        TelemetryReadout(
            values = listOf(
                TelemetryValue("RAM", "${s.ramUsedPct}% · ${s.ramUsedMb}/${s.ramTotalMb} MB", s.ramUsedPct > 85),
                TelemetryValue(
                    "STORAGE",
                    "${s.storageUsedPct}% · ${"%.1f".format(s.storageFreeGb)} GB free",
                    s.storageUsedPct > 90
                ),
                TelemetryValue(
                    "BATTERY",
                    "${s.batteryPct}% · ${s.batteryTempC}°C",
                    s.batteryPct in 1..15 || s.batteryTempC >= 42f
                ),
                TelemetryValue("UPTIME", formatUptime(s.uptimeMs)),
                TelemetryValue("LINK", s.network + if (s.vpn) " · vpn" else "")
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePill(
                text = if (state.notificationAccess) "NOTIFY OK" else "NOTIFY OFF",
                color = if (state.notificationAccess) Success else Warning
            )
            StatePill(
                text = if (state.screenAccess) "SCREEN OK" else "SCREEN OFF",
                color = if (state.screenAccess) Success else Warning
            )
        }
    }
}

private fun formatUptime(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
