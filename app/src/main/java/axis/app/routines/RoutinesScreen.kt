package axis.app.routines

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.act.routine.Routine
import axis.act.routine.RoutineEngine
import axis.act.routine.RoutineRepository
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.GlassChip
import axis.ui.components.SettingsSwitchRow
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val routines: List<Routine> = emptyList(),
    val running: Boolean = false
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repo: RoutineRepository,
    private val engine: RoutineEngine
) : ViewModel() {

    val state: StateFlow<RoutinesUiState> = kotlinx.coroutines.flow.combine(
        repo.routines,
        engine.running
    ) { list, running -> RoutinesUiState(list, running) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())

    val hapticsNote: StateFlow<String?> = repo.routines
        .map { list -> list.firstOrNull { it.lastResult != null }?.let { "${it.name}: ${it.lastResult}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setEnabled(id, enabled)
            // The engine only ticks while something is armed.
            val anyEnabled = repo.load().any { it.enabled }
            if (anyEnabled && !engine.running.value) engine.start()
            if (!anyEnabled && engine.running.value) engine.stop()
        }
    }

    fun runNow(id: String) {
        if (!engine.running.value) engine.start()
        engine.runNow(id)
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun toggleEngine() {
        if (engine.running.value) engine.stop() else engine.start()
    }
}

/**
 * Routines gallery (spec §S9): real routines, real triggers, real runs.
 * Flow Studio (the editor) is one tap away on each card.
 */
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: RoutinesViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val note by vm.hapticsNote.collectAsStateWithLifecycle()

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
                    Text("Routines", style = AxisType.Title)
                    Text(
                        if (state.running) "engine running · 30s tick"
                        else "engine idle — arm a routine",
                        style = AxisType.Caption,
                        color = if (state.running) Success else TextSecondary
                    )
                }
                GlassChip(
                    label = if (state.running) "stop" else "start",
                    active = state.running,
                    onClick = vm::toggleEngine
                )
            }

            note?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AxisType.Telemetry.copy(color = TextSecondary))
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("GALLERY", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))

            state.routines.forEach { routine ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AxisSpacing.cardGap),
                    accent = if (routine.enabled) axis.ui.components.GlassAccent.CYAN
                    else axis.ui.components.GlassAccent.NONE
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            routine.name,
                            style = AxisType.BodyStrong.copy(color = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        if (routine.builtIn) GlassChip(label = "built-in", compact = true)
                    }
                    if (routine.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(routine.description, style = AxisType.Caption, color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("WHEN", style = AxisType.Caption)
                    Text(routine.summary, style = AxisType.Telemetry.copy(color = AccentCyan))
                    Spacer(Modifier.height(6.dp))
                    Text("THEN", style = AxisType.Caption)
                    routine.actions.forEach { action ->
                        Text("· ${action.label}", style = AxisType.Telemetry)
                    }
                    routine.lastResult?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "last: $it",
                            style = AxisType.Caption,
                            color = if (it.contains("fail", true)) Danger else TextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingsSwitchRow(
                        title = "Armed",
                        checked = routine.enabled,
                        onCheckedChange = { vm.setEnabled(routine.id, it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AxisButton(
                            text = "Run now",
                            style = AxisButtonStyle.SECONDARY,
                            onClick = { vm.runNow(routine.id) }
                        )
                        AxisButton(
                            text = "Edit",
                            style = AxisButtonStyle.SECONDARY,
                            onClick = { onEdit(routine.id) }
                        )
                        if (!routine.builtIn) {
                            AxisButton(
                                text = "Delete",
                                style = AxisButtonStyle.DANGER,
                                onClick = { vm.delete(routine.id) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
