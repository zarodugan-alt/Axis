package axis.app.sidecar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.sense.DeviceStateRepository
import axis.sense.DeviceSnapshot
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.DotStatus
import axis.ui.components.GlassCard
import axis.ui.components.StatusDot
import axis.ui.components.TelemetryReadout
import axis.ui.components.TelemetryValue
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SystemStatusUiState(
    val rows: List<SystemRow> = emptyList(),
    val snapshot: DeviceSnapshot = DeviceSnapshot()
)

@HiltViewModel
class SystemStatusViewModel @Inject constructor(
    private val checks: PermissionChecks,
    private val device: DeviceStateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SystemStatusUiState())
    val state: StateFlow<SystemStatusUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _state.value = SystemStatusUiState(
                    rows = checks.systems(),
                    snapshot = device.sample()
                )
                delay(3_000)
            }
        }
    }
}

/**
 * SYSTEM STATUS (spec §S4): the full permission/service panel, standalone
 * from the Side-Car overlay. Every row is a live platform check with a real
 * fix action — no row ever claims a capability the device has not granted.
 */
@Composable
fun SystemStatusScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: SystemStatusViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AxisSpacing.screen)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("System status", style = AxisType.Title)
                    Text("Live capability checks", style = AxisType.Caption, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("PERMISSIONS & SERVICES", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                state.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(row.status)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.label, style = AxisType.BodyStrong.copy(color = TextPrimary))
                            Text(row.detail, style = AxisType.Caption, color = TextSecondary)
                        }
                        val fix = row.fix
                        if (fix != null) {
                            AxisButton(
                                text = "Fix",
                                style = AxisButtonStyle.SECONDARY,
                                onClick = {
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
                            )
                        } else if (row.status == DotStatus.ON) {
                            Text("ok", style = AxisType.Telemetry.copy(color = axis.ui.theme.Success))
                        }
                    }
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("DEVICE", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val s = state.snapshot
                TelemetryReadout(
                    values = listOf(
                        TelemetryValue("battery", "${s.batteryPct}% · ${s.batteryTempC}°C", s.batteryTempC >= 42f),
                        TelemetryValue("network", s.network + if (s.vpn) " · vpn" else ""),
                        TelemetryValue("ram", "${s.ramUsedPct}% · ${s.ramUsedMb}/${s.ramTotalMb} MB", s.ramUsedPct > 85),
                        TelemetryValue("storage", "${s.storageFreeGb} GB free", s.storageUsedPct > 90),
                        TelemetryValue("cpu", "${(s.cpuLoad * 100).toInt()}% · ${s.cpuCores} cores"),
                        TelemetryValue("thermal", "${s.thermalC}°C"),
                        TelemetryValue("torch", if (s.torchOn) "on" else "off"),
                        TelemetryValue("dnd", if (s.dndOn) "on" else "off"),
                        TelemetryValue(
                            "rotation",
                            if (s.rotationLocked) "locked" else "auto"
                        ),
                        TelemetryValue("brightness", "${s.brightness * 100 / 255}%")
                    )
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
