package axis.app.safety

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.app.drawer.AppRepository
import axis.kernel.agent.RiskLevel
import axis.safety.AuditEntry
import axis.safety.AuditLog
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.GlassChip
import axis.ui.components.SettingsRadioRow
import axis.ui.components.SettingsSwitchRow
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SafetyUiState(
    val killSwitch: Boolean = false,
    val confirmFrom: RiskLevel = RiskLevel.MEDIUM,
    val rateLimit: Int = 12,
    val protectedApps: Set<String> = emptySet(),
    val apps: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val apps: AppRepository,
    private val auditLog: AuditLog
) : ViewModel() {

    private val auditRefresh = MutableStateFlow(0)

    val state: StateFlow<SafetyUiState> = kotlinx.coroutines.flow.combine(
        settings.killSwitchState,
        settings.confirmFrom,
        settings.rateLimit,
        settings.protectedApps,
        apps.visibleApps
    ) { kill, confirm, rate, protected, appList ->
        SafetyUiState(
            killSwitch = kill,
            confirmFrom = runCatching { RiskLevel.valueOf(confirm) }.getOrDefault(RiskLevel.MEDIUM),
            rateLimit = rate,
            protectedApps = protected,
            apps = appList.map { it.packageName to it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SafetyUiState())

    /** Audit trail is in-memory today; refreshed on demand. */
    fun audit(): List<AuditEntry> {
        auditRefresh.value
        return auditLog.all()
    }

    fun setKillSwitch(engaged: Boolean) {
        viewModelScope.launch { settings.setKillSwitch(engaged) }
    }

    fun setConfirmFrom(level: RiskLevel) {
        viewModelScope.launch { settings.setConfirmFrom(level.name) }
    }

    fun setRateLimit(perMinute: Int) {
        viewModelScope.launch { settings.setRateLimit(perMinute) }
    }

    fun toggleProtected(pkg: String) {
        viewModelScope.launch { settings.setProtected(pkg, pkg !in state.value.protectedApps) }
    }

    fun refreshAudit() {
        auditRefresh.value += 1
    }
}

/**
 * Safety console (spec §S10 · §F14): the kill switch, the confirmation
 * threshold, the protected-app list and the audit trail of everything the
 * agent did.
 */
@Composable
fun SafetyScreen(
    onBack: () -> Unit,
    vm: SafetyViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAudit by remember { mutableStateOf(false) }
    vm.refreshAudit()
    val entries = if (showAudit) vm.audit() else emptyList()

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
                    Text("Safety", style = AxisType.Title)
                    Text(
                        if (state.killSwitch) "kill switch engaged" else "gates active",
                        style = AxisType.Caption,
                        color = if (state.killSwitch) Danger else Success
                    )
                }
                GlassChip(
                    label = if (state.killSwitch) "RESUME" else "STOP ALL",
                    active = state.killSwitch,
                    accent = Danger,
                    onClick = { vm.setKillSwitch(!state.killSwitch) }
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accent = if (state.killSwitch) axis.ui.components.GlassAccent.DANGER
                else axis.ui.components.GlassAccent.NONE
            ) {
                SettingsSwitchRow(
                    title = "Kill switch",
                    subtitle = "Stops routines and blocks every agent action immediately",
                    checked = state.killSwitch,
                    onCheckedChange = vm::setKillSwitch
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("CONFIRM BEFORE RUNNING", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                RiskLevel.entries.forEach { level ->
                    SettingsRadioRow(
                        title = level.name.lowercase().replaceFirstChar { it.uppercase() },
                        subtitle = when (level) {
                            RiskLevel.TRIVIAL -> "never ask (reads only)"
                            RiskLevel.LOW -> "ask for anything that changes state"
                            RiskLevel.MEDIUM -> "balanced — recommended"
                            RiskLevel.HIGH -> "only the smallest actions run freely"
                            RiskLevel.CRITICAL -> "ask for everything"
                        },
                        selected = state.confirmFrom == level,
                        onClick = { vm.setConfirmFrom(level) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Rate limit: ${state.rateLimit} actions/minute",
                style = AxisType.Telemetry.copy(color = TextSecondary)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisButton("−", style = AxisButtonStyle.SECONDARY, onClick = { vm.setRateLimit(state.rateLimit - 2) })
                AxisButton("+", style = AxisButtonStyle.SECONDARY, onClick = { vm.setRateLimit(state.rateLimit + 2) })
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("PROTECTED APPS (${state.protectedApps.size})", style = AxisType.Section)
            Spacer(Modifier.height(4.dp))
            Text(
                "The agent may never launch or modify these, whatever the model asks.",
                style = AxisType.Caption,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                state.apps.take(14).forEach { (pkg, label) ->
                    SettingsSwitchRow(
                        title = label,
                        subtitle = pkg,
                        checked = pkg in state.protectedApps,
                        onCheckedChange = { vm.toggleProtected(pkg) }
                    )
                }
                if (state.apps.size > 14) {
                    Text(
                        "+${state.apps.size - 14} more in the drawer's app menu",
                        style = AxisType.Caption,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AUDIT TRAIL", style = AxisType.Section, modifier = Modifier.weight(1f))
                GlassChip(
                    label = if (showAudit) "hide" else "show",
                    onClick = {
                        showAudit = !showAudit
                        vm.refreshAudit()
                    }
                )
            }
            if (showAudit) {
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text("Nothing recorded yet.", style = AxisType.Caption, color = TextSecondary)
                }
                entries.take(40).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (entry.ok) "·" else "×",
                            style = AxisType.Telemetry.copy(color = if (entry.ok) AccentCyan else Danger)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${entry.kind} · ${entry.tool}",
                                style = AxisType.Telemetry.copy(color = AccentCyan)
                            )
                            Text(entry.detail.take(120), style = AxisType.Caption, maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
