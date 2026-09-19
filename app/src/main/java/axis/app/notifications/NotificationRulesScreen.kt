package axis.app.notifications

import androidx.compose.foundation.clickable
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
import axis.app.data.SettingsStore
import axis.app.drawer.AppRepository
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.AxisDialog
import axis.ui.components.GlassCard
import axis.ui.components.SettingsSwitchRow
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.TextSecondary
import axis.ui.theme.glass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val priorityApps: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val quietEnabled: Boolean = false,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val apps: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class NotificationRulesViewModel @Inject constructor(
    private val settings: SettingsStore,
    apps: AppRepository
) : ViewModel() {

    val state: StateFlow<RulesUiState> = combine(
        settings.priorityApps,
        settings.triageKeywords,
        combine(settings.quietHoursEnabled, settings.quietWindow) { on, window ->
            Triple(on, window.first, window.second)
        },
        apps.visibleApps
    ) { priority, keywords, quiet, appList ->
        RulesUiState(
            priorityApps = priority,
            keywords = keywords,
            quietEnabled = quiet.first,
            quietStart = quiet.second,
            quietEnd = quiet.third,
            apps = appList.map { it.packageName to it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RulesUiState())

    fun togglePriority(pkg: String) {
        viewModelScope.launch { settings.setPriorityApp(pkg, pkg !in state.value.priorityApps) }
    }

    fun addKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        if (clean.isBlank()) return
        viewModelScope.launch { settings.setTriageKeyword(clean, true) }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch { settings.setTriageKeyword(keyword, false) }
    }

    fun setQuiet(enabled: Boolean) {
        val s = state.value
        viewModelScope.launch { settings.setQuietHours(enabled, s.quietStart, s.quietEnd) }
    }

    fun setQuietWindow(start: Int, end: Int) {
        viewModelScope.launch {
            settings.setQuietHours(state.value.quietEnabled, start, end)
        }
    }
}

/**
 * Notification Rules (spec §S5): which notifications break through, which are
 * held, and when quiet hours apply. Every rule here is consumed by
 * `NotificationTriage` — the screen cannot express a rule the triage engine
 * does not understand.
 */
@Composable
fun NotificationRulesScreen(
    onBack: () -> Unit,
    vm: NotificationRulesViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var keywordInput by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }

    if (picker) {
        AxisDialog(
            title = "Priority app",
            onDismiss = { picker = false },
            body = {
                Text(
                    "Notifications from priority apps always break through, even in quiet hours.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.apps.take(24).forEach { (pkg, label) ->
                        SettingsSwitchRow(
                            title = label,
                            subtitle = pkg,
                            checked = pkg in state.priorityApps,
                            onCheckedChange = { vm.togglePriority(pkg) }
                        )
                    }
                }
            },
            buttons = {
                AxisButton(
                    text = "Done",
                    onClick = { picker = false }
                )
            }
        )
    }

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
                Text("Notification Rules", style = AxisType.Title)
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("BREAK THROUGH", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Priority apps (${state.priorityApps.size}) always surface; everything " +
                        "else is scored, and low scores are held in the Notification Center " +
                        "instead of interrupting you.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                AxisButton(
                    text = "Choose priority apps",
                    style = AxisButtonStyle.SECONDARY,
                    onClick = { picker = true }
                )
                if (state.priorityApps.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.priorityApps.forEach { pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.apps.firstOrNull { it.first == pkg }?.second ?: pkg,
                                style = AxisType.Telemetry.copy(color = AccentCyan),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.togglePriority(pkg) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    tint = Danger,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("KEYWORDS", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Any notification whose title or text contains one of these words breaks through.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        singleLine = true,
                        textStyle = AxisType.Body.copy(color = axis.ui.theme.TextPrimary),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .glass(corner = 12.dp)
                            .padding(10.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            vm.addKeyword(keywordInput)
                            keywordInput = ""
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add keyword", tint = AccentCyan)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.keywords.isEmpty()) {
                    Text("No keywords yet.", style = AxisType.Caption, color = TextSecondary)
                }
                state.keywords.forEach { keyword ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            keyword,
                            style = AxisType.Telemetry.copy(color = AccentCyan),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.removeKeyword(keyword) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove keyword",
                                tint = Danger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("QUIET HOURS", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchRow(
                    title = "Hold notifications overnight",
                    subtitle = "${formatMinute(state.quietStart)} → ${formatMinute(state.quietEnd)}",
                    checked = state.quietEnabled,
                    onCheckedChange = vm::setQuiet
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(21 * 60, 22 * 60, 23 * 60).forEach { start ->
                        Chip(
                            label = "from ${formatMinute(start)}",
                            active = state.quietStart == start,
                            onClick = { vm.setQuietWindow(start, state.quietEnd) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6 * 60, 7 * 60, 8 * 60).forEach { end ->
                        Chip(
                            label = "until ${formatMinute(end)}",
                            active = state.quietEnd == end,
                            onClick = { vm.setQuietWindow(state.quietStart, end) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .glass(
                corner = 50.dp,
                borderColor = if (active) AccentCyan.copy(alpha = 0.6f) else axis.ui.theme.GlassBorder
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = AxisType.Caption.copy(color = if (active) AccentCyan else TextSecondary))
    }
}

private fun formatMinute(minute: Int): String =
    "%02d:%02d".format(minute / 60, minute % 60)
