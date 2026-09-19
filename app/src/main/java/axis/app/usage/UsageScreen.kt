package axis.app.usage

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.agent.usage.UsageLedger
import axis.agent.usage.latencySeries
import axis.app.data.UsageRepository
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.Sparkline
import axis.ui.components.StatusPill
import axis.ui.components.TelemetryReadout
import axis.ui.components.TelemetryValue
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val ledger: UsageLedger,
    private val repository: UsageRepository
) : ViewModel() {

    val summary: StateFlow<axis.agent.usage.UsageSummary> = ledger.summary
    val records: StateFlow<List<axis.agent.usage.UsageRecord>> = ledger.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clear() {
        viewModelScope.launch {
            ledger.clear()
            repository.clear()
        }
    }

    /** Exported JSONL, surfaced to the caller for the share sheet. */
    suspend fun export(): String = repository.exportText()
}

/** Usage dashboard (spec §S10 · §F7): every request, token and failure. */
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    vm: UsageViewModel = hiltViewModel()
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

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
                Text("Usage", style = AxisType.Title)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Session ring of the last ${records.size} requests · persisted to usage.jsonl",
                style = AxisType.Caption,
                color = TextSecondary
            )

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                TelemetryReadout(
                    values = listOf(
                        TelemetryValue("requests", summary.requests.toString()),
                        TelemetryValue("failures", summary.failures.toString(), summary.failures > 0),
                        TelemetryValue("tokens", "~${summary.tokens}"),
                        TelemetryValue("avg latency", "${summary.avgLatencyMs} ms"),
                        TelemetryValue("last 24h", summary.last24h.toString())
                    )
                )
                Spacer(Modifier.height(10.dp))
                Sparkline(
                    data = records.latencySeries().ifEmpty { listOf(0f, 0f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("RECENT", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            if (records.isEmpty()) {
                Text("No requests yet.", style = AxisType.Caption, color = TextSecondary)
            }
            records.asReversed().take(40).forEach { record ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = record.providerId,
                        color = if (record.ok) AccentCyan else Danger
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${record.kind} · ${record.model}",
                            style = AxisType.Telemetry.copy(color = TextPrimary)
                        )
                        Text(
                            "${record.latencyMs} ms · ${record.totalTokens} tok" +
                                (if (record.approx) " (est)" else "") +
                                (record.error?.let { " · ${it.take(60)}" } ?: ""),
                            style = AxisType.Caption,
                            color = if (record.ok) TextSecondary else Danger,
                            maxLines = 2
                        )
                    }
                    Text(formatter.format(Date(record.ts)), style = AxisType.Caption)
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AxisButton(
                    text = "Clear log",
                    style = AxisButtonStyle.DANGER,
                    onClick = vm::clear
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
