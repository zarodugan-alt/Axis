package axis.app.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.app.chat.ChatHandoff
import axis.app.nav.Routes
import axis.ui.circuit.CircuitBoard
import axis.ui.components.AxisOrb
import axis.ui.components.OrbState
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextSecondary
import axis.ui.theme.glass
import axis.ui.theme.glow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AXIS home board (spec §S2, redesigned): a powered circuit board rather
 * than an icon grid.
 *
 * Top to bottom: silkscreen header → the AI core seated in its IC package →
 * live subsystem modules → a functional tool rail → a terminal that searches
 * apps, asks AXIS, or falls back to the web → telemetry.
 *
 * Apps are deliberately absent: they live in the drawer, one swipe away.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val greeting by vm.greeting.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val action by vm.lastAction.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var torchAsked by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.toggleTorch(true) }

    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    CircuitBoard(
        modifier = Modifier.fillMaxSize(),
        pulseCount = 8,
        intensity = if (state.killSwitch) 0.55f else 1f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = AxisSpacing.screen)
        ) {
            Spacer(Modifier.height(10.dp))

            BoardHeader(
                clock = clock.format(Date()),
                status = buildString {
                    append(state.snapshot.network.uppercase())
                    append(" · ")
                    append(state.snapshot.batteryPct)
                    append("%")
                    if (state.snapshot.charging) append("+")
                },
                accent = if (state.killSwitch) Danger else AccentCyan
            )

            Spacer(Modifier.height(AxisSpacing.section))

            // --------------------------------------------------- core module
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(
                        corner = 24.dp,
                        borderColor = if (state.killSwitch) Danger.copy(alpha = 0.6f)
                        else AccentCyan.copy(alpha = 0.45f)
                    )
                    .let { if (state.killSwitch) it.glow(Danger, 20.dp) else it }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AXIS-C1 // CORE", style = AxisType.Telemetry.copy(color = TextSecondary))
                    StatePill(
                        text = if (state.killSwitch) "KILL SWITCH" else "ONLINE",
                        color = if (state.killSwitch) Danger else Success
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .glass(corner = 18.dp)
                        .pointerInput(state.providersConnected) {
                            detectTapGestures(
                                onTap = {
                                    AxisHaptics.press(view, state.ready)
                                    onNavigate(
                                        if (state.providersConnected == 0) Routes.SETTINGS_PROVIDERS
                                        else Routes.CHAT
                                    )
                                },
                                onLongPress = {
                                    AxisHaptics.longPress(view, state.ready)
                                    onNavigate(Routes.SETTINGS_VOICE)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AxisOrb(state = OrbState.IDLE, orbSize = 116.dp)
                }
                Spacer(Modifier.height(10.dp))
                Text(greeting, style = AxisType.Title)
                Text(
                    if (state.providersConnected == 0) "tap to connect an AI provider"
                    else "${state.providersConnected} provider" +
                        (if (state.providersConnected == 1) "" else "s") +
                        " wired · tap to talk · hold to speak",
                    style = AxisType.Caption
                )
            }

            action?.let { message ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(corner = 12.dp, borderColor = axis.ui.theme.Warning.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("!", style = AxisType.BodyStrong)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        message,
                        style = AxisType.Caption,
                        color = axis.ui.theme.Warning,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))

            Text("SUBSYSTEMS", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            ModuleGrid(
                state = state,
                onNavigate = onNavigate,
                haptics = { AxisHaptics.tick(view, state.ready) }
            )

            Spacer(Modifier.height(AxisSpacing.section))

            Text("TOOLS", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            ToolRail(
                state = state,
                onTorch = { wanted ->
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted || torchAsked) {
                        vm.toggleTorch(wanted)
                    } else {
                        torchAsked = true
                        runCatching { cameraPermission.launch(Manifest.permission.CAMERA) }
                    }
                },
                onDnd = vm::toggleDnd,
                onRotation = vm::toggleRotation,
                onBrightness = vm::nudgeBrightness,
                onSettings = vm::openSettingsPage,
                onKillSwitch = vm::setKillSwitch,
                haptics = { AxisHaptics.press(view, state.ready) }
            )

            Spacer(Modifier.height(AxisSpacing.section))

            Text("TERMINAL", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            Terminal(
                query = query,
                results = results,
                onQueryChange = vm::setQuery,
                iconFor = vm::iconFor,
                onLaunch = { pkg ->
                    AxisHaptics.press(view, state.ready)
                    vm.launch(pkg)
                },
                onAsk = { q ->
                    AxisHaptics.press(view, state.ready)
                    vm.setQuery("")
                    ChatHandoff.prompt.value = q
                    onNavigate(Routes.CHAT)
                },
                onWeb = { q ->
                    AxisHaptics.press(view, state.ready)
                    vm.webSearch(q)
                },
                onAppInfo = vm::openAppInfo,
                haptics = { AxisHaptics.longPress(view, state.ready) }
            )

            Spacer(Modifier.height(AxisSpacing.section))

            Text("TELEMETRY", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            TelemetryPanel(state = state)

            Spacer(Modifier.height(28.dp))
            Text(
                "APPS LIVE IN THE DRAWER — SWIPE LEFT",
                style = AxisType.Caption.copy(color = TextSecondary)
            )
            Spacer(Modifier.height(64.dp))
        }
    }
}
