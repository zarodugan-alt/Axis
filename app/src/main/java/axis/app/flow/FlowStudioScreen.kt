package axis.app.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.act.routine.Routine
import axis.act.routine.RoutineAction
import axis.act.routine.RoutineRepository
import axis.act.routine.RoutineTrigger
import axis.app.drawer.AppRepository
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassChip
import axis.ui.components.GlassCard
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.glass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class FlowStudioViewModel @Inject constructor(
    private val repo: RoutineRepository,
    private val apps: AppRepository
) : ViewModel() {

    private val _routine = MutableStateFlow<Routine?>(null)
    val routine: StateFlow<Routine?> = _routine.asStateFlow()

    private val _appLabels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val appLabels: StateFlow<List<Pair<String, String>>> = _appLabels

    fun load(id: String?) {
        viewModelScope.launch {
            _routine.value = if (id == null) {
                Routine(id = repo.newId(), name = "New routine", enabled = false)
            } else {
                repo.load().firstOrNull { it.id == id }
                    ?: Routine(id = id, name = "Unknown", enabled = false)
            }
            _appLabels.value = apps.visibleApps.first()
                .filterNot { it.isSystem }
                .take(30)
                .map { it.packageName to it.label }
        }
    }

    private fun mutate(block: (Routine) -> Routine) {
        _routine.value = _routine.value?.let(block)
    }

    fun setName(name: String) = mutate { it.copy(name = name) }

    fun setCooldown(seconds: Int) = mutate { it.copy(cooldownSeconds = seconds.coerceIn(30, 3600)) }

    fun addTrigger(trigger: RoutineTrigger) = mutate { it.copy(triggers = it.triggers + trigger) }

    fun removeTrigger(index: Int) = mutate { r ->
        r.copy(triggers = r.triggers.filterIndexed { i, _ -> i != index })
    }

    fun addAction(action: RoutineAction) = mutate { it.copy(actions = it.actions + action) }

    fun removeAction(index: Int) = mutate { r ->
        r.copy(actions = r.actions.filterIndexed { i, _ -> i != index })
    }

    fun save(onDone: () -> Unit) {
        val current = _routine.value ?: return
        viewModelScope.launch {
            repo.upsert(current)
            onDone()
        }
    }
}

/**
 * Flow Studio (spec §S8): the routine editor. Triggers and actions are picked
 * from the vocabulary the engine actually implements — the editor cannot
 * build a routine the engine would refuse to run.
 */
@Composable
fun FlowStudioScreen(
    routineId: String?,
    onBack: () -> Unit,
    vm: FlowStudioViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(routineId) { vm.load(routineId) }
    val routine by vm.routine.collectAsStateWithLifecycle()
    val apps by vm.appLabels.collectAsStateWithLifecycle()
    val current = routine

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
                Text("Flow Studio", style = AxisType.Title)
            }

            if (current == null) {
                Spacer(Modifier.height(24.dp))
                Text("Loading…", style = AxisType.Body, color = TextSecondary)
                return@Column
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("NAME", style = AxisType.Caption)
                Input(value = current.name, onValueChange = vm::setName)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cooldown", style = AxisType.BodyStrong, modifier = Modifier.weight(1f))
                    Stepper(
                        value = "${current.cooldownSeconds}s",
                        onMinus = { vm.setCooldown(current.cooldownSeconds - 60) },
                        onPlus = { vm.setCooldown(current.cooldownSeconds + 60) }
                    )
                }
            }

            // ------------------------------------------------------- triggers
            Spacer(Modifier.height(AxisSpacing.section))
            Text("WHEN (${current.triggers.size})", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                current.triggers.forEachIndexed { index, trigger ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(trigger.label, style = AxisType.Telemetry.copy(color = AccentCyan), modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeTrigger(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove trigger", tint = Danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (current.triggers.isEmpty()) {
                    Text("No triggers — the routine is manual only.", style = AxisType.Caption, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("ADD A TRIGGER", style = AxisType.Caption)
                Spacer(Modifier.height(6.dp))
                Chips(
                    items = listOf(
                        "07:00 daily" to { vm.addTrigger(RoutineTrigger.Time(7, 0)) },
                        "23:00 daily" to { vm.addTrigger(RoutineTrigger.Time(23, 0)) },
                        "charging" to { vm.addTrigger(RoutineTrigger.Charging) },
                        "battery < 20%" to { vm.addTrigger(RoutineTrigger.BatteryBelow(20)) },
                        "battery > 80%" to { vm.addTrigger(RoutineTrigger.BatteryAbove(80)) },
                        "wi-fi on" to { vm.addTrigger(RoutineTrigger.WifiConnected()) },
                        "manual" to { vm.addTrigger(RoutineTrigger.Manual) }
                    )
                )
                if (apps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("WHEN I OPEN", style = AxisType.Caption)
                    Spacer(Modifier.height(6.dp))
                    Chips(
                        items = apps.take(8).map { (pkg, label) ->
                            label to { vm.addTrigger(RoutineTrigger.AppLaunched(pkg)) }
                        }
                    )
                }
            }

            // -------------------------------------------------------- actions
            Spacer(Modifier.height(AxisSpacing.section))
            Text("THEN (${current.actions.size})", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                current.actions.forEachIndexed { index, action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(action.label, style = AxisType.Telemetry.copy(color = AccentViolet), modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeAction(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove action", tint = Danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (current.actions.isEmpty()) {
                    Text("No actions yet.", style = AxisType.Caption, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("ADD AN ACTION", style = AxisType.Caption)
                Spacer(Modifier.height(6.dp))
                Chips(
                    items = listOf(
                        "silent" to { vm.addAction(RoutineAction.SetRinger(0)) },
                        "vibrate" to { vm.addAction(RoutineAction.SetRinger(1)) },
                        "normal" to { vm.addAction(RoutineAction.SetRinger(2)) },
                        "DND on" to { vm.addAction(RoutineAction.SetDnd(true)) },
                        "DND off" to { vm.addAction(RoutineAction.SetDnd(false)) },
                        "bright 30%" to { vm.addAction(RoutineAction.SetBrightness(30)) },
                        "bright 80%" to { vm.addAction(RoutineAction.SetBrightness(80)) },
                        "lock rotation" to { vm.addAction(RoutineAction.SetRotationLocked(true)) },
                        "unlock rotation" to { vm.addAction(RoutineAction.SetRotationLocked(false)) },
                        "open wifi page" to { vm.addAction(RoutineAction.OpenSettings("wifi")) },
                        "say good night" to { vm.addAction(RoutineAction.Say("Good night.")) },
                        "notify me" to { vm.addAction(RoutineAction.Notify(current.name, "Routine ran")) }
                    )
                )
                if (apps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("LAUNCH APP", style = AxisType.Caption)
                    Spacer(Modifier.height(6.dp))
                    Chips(
                        items = apps.take(8).map { (pkg, label) ->
                            label to { vm.addAction(RoutineAction.LaunchApp(pkg)) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            AxisButton(text = "Save routine", onClick = { vm.save(onBack) })
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Input(value: String, onValueChange: (String) -> Unit) {
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = AxisType.Body.copy(color = TextPrimary),
        cursorBrush = SolidColor(AccentCyan),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .glass(corner = 12.dp)
            .padding(10.dp)
    )
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", onMinus)
        Text(value, style = AxisType.Telemetry.copy(color = TextPrimary), modifier = Modifier.padding(horizontal = 10.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    BoxedChip(label = label, accent = TextSecondary, onClick = onClick)
}

@Composable
private fun Chips(items: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, onClick) ->
                    BoxedChip(label = label, accent = AccentCyan, onClick = onClick, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BoxedChip(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .glass(corner = 50.dp, borderColor = accent.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = AxisType.Caption.copy(color = accent))
    }
}
