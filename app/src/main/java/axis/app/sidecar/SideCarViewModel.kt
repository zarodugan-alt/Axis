package axis.app.sidecar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.ui.components.TelemetryValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskRow(
    val title: String,
    val stepLabel: String,
    val progress: Float,
    val done: Boolean
)

data class SideCarUiState(
    val systems: List<SystemRow> = emptyList(),
    val tasks: List<TaskRow> = emptyList(),
    val telemetry: List<TelemetryValue> = emptyList(),
    val mode: String = "off"
)

/**
 * Side-Car state (spec §S4): 2s ticker refreshes telemetry + system rows
 * while the panel is open. Tasks stay empty until the P3 agent reports
 * progress; modes persist now, behavior wires in P4.
 */
@HiltViewModel
class SideCarViewModel @Inject constructor(
    private val checks: PermissionChecks,
    private val telemetry: TelemetryRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val uiState: StateFlow<SideCarUiState> =
        combine(ticker, settings.sideCarMode) { _, mode ->
            val s = telemetry.sample()
            SideCarUiState(
                systems = checks.systems(),
                tasks = emptyList(), // P3: agent TaskProgress events
                telemetry = listOf(
                    TelemetryValue("RAM", "${s.ramPct}%", s.ramPct > 85),
                    TelemetryValue("BAT", "${s.batPct}%", s.batPct in 1..15),
                    TelemetryValue("TEMP", "${s.batTempC}°C", s.batTempC >= 42f),
                    TelemetryValue("STATE", if (s.charging) "charging" else "on battery")
                ),
                mode = mode
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SideCarUiState())

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setMode(mode: String) {
        viewModelScope.launch { settings.setSideCarMode(mode) }
    }

    companion object {
        private val ticker: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(2_000)
            }
        }
    }
}
