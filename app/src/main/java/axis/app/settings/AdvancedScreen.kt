package axis.app.settings

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.app.data.UsageRepository
import axis.safety.AuditLog
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.SettingsNavRow
import axis.ui.components.SettingsSliderRow
import axis.ui.components.SettingsSwitchRow
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AdvancedViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val usageRepository: UsageRepository,
    private val auditLog: AuditLog
) : ViewModel() {

    val asrMode: StateFlow<String> = settings.asrMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")
    val webhookPort: StateFlow<Int> = settings.webhookPort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val haptics: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val exportText = MutableStateFlow("")

    fun setWebhookPort(port: Int) {
        viewModelScope.launch { settings.setWebhookPort(port) }
    }

    fun setHaptics(on: Boolean) {
        viewModelScope.launch { settings.setHaptics(on) }
    }

    /** Builds the full settings+logs export into [exportText]. */
    fun buildExport() {
        viewModelScope.launch {
            val usage = runCatching { usageRepository.exportText() }.getOrDefault("")
            val audit = auditLog.exportText()
            exportText.value = buildString {
                appendLine("# AXIS export " + java.util.Date())
                appendLine("# --- audit log ---")
                appendLine(audit.ifBlank { "(empty)" })
                appendLine("# --- usage log ---")
                append(usage.ifBlank { "(empty)" })
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            runCatching { usageRepository.clear() }
            auditLog.clear()
            exportText.value = ""
        }
    }
}

/** Advanced (spec §S10): logs, export, webhook port, developer conveniences. */
@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    onFlowStudio: () -> Unit,
    vm: AdvancedViewModel = hiltViewModel()
) {
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val port by vm.webhookPort.collectAsStateWithLifecycle()
    val export by vm.exportText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var portLocal by remember(port) { mutableStateOf(port.toFloat()) }

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
                Text("Advanced", style = AxisType.Title)
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchRow(
                    title = "Haptics",
                    subtitle = "Pulses on taps, page settles and confirmations",
                    checked = haptics,
                    onCheckedChange = vm::setHaptics
                )
                SettingsNavRow(
                    title = "Flow Studio",
                    subtitle = "Edit routines, triggers and actions",
                    onClick = onFlowStudio
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("LOGS & EXPORT", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Export bundles the audit trail and the usage log as plain text. " +
                        "Nothing is uploaded — the text is produced locally for you to copy.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    AxisButton(
                        text = "Build export",
                        style = AxisButtonStyle.SECONDARY,
                        onClick = vm::buildExport
                    )
                    Spacer(Modifier.width(10.dp))
                    AxisButton(
                        text = "Clear logs",
                        style = AxisButtonStyle.DANGER,
                        onClick = vm::clearLogs
                    )
                }
                if (export.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${export.lines().size} lines ready — tap share to send them anywhere",
                        style = AxisType.Caption,
                        color = Success
                    )
                    Spacer(Modifier.height(8.dp))
                    AxisButton(
                        text = "Share export",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "AXIS export")
                                putExtra(Intent.EXTRA_TEXT, export)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, "Share AXIS export")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("FLOW WEBHOOK", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Reserved for the local webhook receiver. 0 disables it; a port only " +
                        "opens while AXIS is in the foreground.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                SettingsSliderRow(
                    title = "Port",
                    value = portLocal,
                    valueRange = 0f..9000f,
                    valueLabel = if (portLocal.toInt() == 0) "off" else portLocal.toInt().toString(),
                    onValueChange = {
                        portLocal = it
                        vm.setWebhookPort(it.toInt())
                    }
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("DANGER ZONE", style = AxisType.Section)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Resetting AXIS clears hidden apps, routine edits, keys and logs. " +
                        "Your installed apps are untouched.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
