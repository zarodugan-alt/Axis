package axis.app.notifications

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
import axis.app.data.SettingsStore
import axis.app.drawer.AppRepository
import axis.sense.notify.NotificationAccess
import axis.sense.notify.NotificationInbox
import axis.sense.notify.NotificationRecord
import axis.sense.notify.NotificationTriage
import axis.sense.notify.QuietHours
import axis.sense.notify.Triage
import axis.sense.notify.TriageRules
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.SettingsSwitchRow
import axis.ui.components.StatusPill
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.AccentCyan
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationUiState(
    val access: Boolean = false,
    val records: List<NotificationRecord> = emptyList(),
    val triage: Map<String, Triage> = emptyMap(),
    val reasons: Map<String, String> = emptyMap(),
    val priorityApps: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val quietHours: QuietHours = QuietHours(0, 0),
    val appLabels: Map<String, String> = emptyMap()
)

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val inbox: NotificationInbox,
    private val settings: SettingsStore,
    private val apps: AppRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val now = System.currentTimeMillis()

    val state: StateFlow<NotificationUiState> = combine(
        inbox.records,
        settings.priorityApps,
        settings.triageKeywords,
        combine(settings.quietHoursEnabled, settings.quietWindow) { on, window ->
            QuietHours(window.first, window.second, enabled = on)
        },
        apps.visibleApps
    ) { records, priority, keywords, quiet, appList ->
        val rules = TriageRules(
            priorityApps = priority,
            keywords = keywords.toList(),
            quietHours = quiet
        )
        val verdicts = records.associate { r ->
            val result = NotificationTriage.decide(r, rules, now)
            r.key to result
        }
        NotificationUiState(
            access = NotificationAccess.isGranted(context),
            records = records,
            triage = verdicts.mapValues { it.value.verdict },
            reasons = verdicts.mapValues { it.value.reason },
            priorityApps = priority,
            keywords = keywords,
            quietHours = quiet,
            appLabels = appList.associate { it.packageName to it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationUiState())

    fun clear() = inbox.clear()

    fun removeFrom(pkg: String) = inbox.removeAllFrom(pkg)

    fun togglePriority(pkg: String) {
        viewModelScope.launch { settings.setPriorityApp(pkg, pkg !in state.value.priorityApps) }
    }

    fun setQuietHours(enabled: Boolean) {
        val current = state.value.quietHours
        viewModelScope.launch {
            settings.setQuietHours(enabled, current.startMinute, current.endMinute)
        }
    }
}

/**
 * Notification Center (spec §S5): everything AXIS captured, sorted and
 * triaged, with the rules that decided each one shown honestly.
 */
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onRules: () -> Unit,
    vm: NotificationCenterViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

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
                    Text("Notifications", style = AxisType.Title)
                    Text(
                        if (state.access) "${state.records.size} held · triage live"
                        else "listener not granted",
                        style = AxisType.Caption,
                        color = if (state.access) TextSecondary else Warning
                    )
                }
                if (state.records.isNotEmpty()) {
                    IconButton(onClick = vm::clear, modifier = Modifier.size(48.dp)) {
                        Text("clear", style = AxisType.Caption.copy(color = Danger))
                    }
                }
            }

            if (!state.access) {
                Spacer(Modifier.height(AxisSpacing.section))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Notification access is off", style = AxisType.BodyStrong)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AXIS can only hold and triage notifications after you grant " +
                            "Notification access in system settings. Nothing is read until then.",
                        style = AxisType.Caption,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchRow(
                    title = "Quiet hours",
                    subtitle = "Hold low-priority notifications overnight",
                    checked = state.quietHours.enabled,
                    onCheckedChange = vm::setQuietHours
                )
                Spacer(Modifier.height(4.dp))
                AxisButton(
                    text = "Triage rules",
                    style = AxisButtonStyle.SECONDARY,
                    onClick = onRules
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            Text("CAPTURED", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))

            if (state.records.isEmpty()) {
                Text(
                    if (state.access) "Nothing captured yet."
                    else "Grant access to start capturing.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
            }

            state.records.forEach { record ->
                val verdict = state.triage[record.key] ?: Triage.NORMAL
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AxisSpacing.cardGap)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.appLabels[record.pkg] ?: record.appLabel,
                            style = AxisType.BodyStrong,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = verdict.name.replace('_', ' '),
                            color = when (verdict) {
                                Triage.BREAK_THROUGH -> Success
                                Triage.NORMAL -> AccentCyan
                                Triage.HOLD -> Warning
                            }
                        )
                    }
                    if (record.displayText.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(record.displayTitle, style = AxisType.Body, maxLines = 1)
                        Text(record.displayText, style = AxisType.Caption, maxLines = 2)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.reasons[record.key].orEmpty(),
                        style = AxisType.Telemetry.copy(color = TextSecondary)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AxisButton(
                            text = if (record.pkg in state.priorityApps) "Un-prioritise" else "Prioritise",
                            style = AxisButtonStyle.SECONDARY,
                            onClick = { vm.togglePriority(record.pkg) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
