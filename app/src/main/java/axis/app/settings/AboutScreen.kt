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
import androidx.compose.material.icons.rounded.OpenInNew
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
import axis.app.drawer.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import axis.ui.components.AxisOrb
import axis.ui.components.GlassCard
import axis.ui.components.OrbState
import axis.ui.components.TelemetryReadout
import axis.ui.components.TelemetryValue
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.TextSecondary

@HiltViewModel
class AboutViewModel @Inject constructor(apps: AppRepository) : ViewModel() {
    val installedApps: StateFlow<Int> = apps.visibleApps
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

/** About (spec §S10): what AXIS is, what it can reach, and the local-only promise. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    vm: AboutViewModel = hiltViewModel()
) {
    val installedApps by vm.installedApps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AxisSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text("About", style = AxisType.Title)
            }

            Spacer(Modifier.height(AxisSpacing.section))
            AxisOrb(state = OrbState.IDLE, orbSize = 96.dp)
            Spacer(Modifier.height(12.dp))
            Text("AXIS", style = AxisType.Title)
            Text("localhost launcher · v0.4.0", style = AxisType.Caption, color = TextSecondary)

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "AXIS is a local-first launcher. Your apps, notifications, routines and " +
                        "API keys live on this device; the only network traffic is the calls you " +
                        "configure, straight to the provider you chose. There is no AXIS account " +
                        "and no AXIS server.",
                    style = AxisType.Body
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                TelemetryReadout(
                    values = listOf(
                        TelemetryValue("build", "0.4.0 · debug"),
                        TelemetryValue("modules", "kernel · agent · sense · act · safety · ui"),
                        TelemetryValue("apps indexed", installedApps.toString()),
                        TelemetryValue("stack", "100% Compose · minSdk 28"),
                        TelemetryValue("storage", "DataStore · AES-GCM keystore")
                    )
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("OPEN SOURCE", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                Text(
                    "AXIS is built only on AndroidX, Kotlin coroutines and Timber. " +
                        "Nothing else ships in the APK — every other capability is written " +
                        "in this repository.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Licences: Apache-2.0 (AndroidX), Apache-2.0 (Kotlin), Apache-2.0 (Timber)",
                        style = AxisType.Caption,
                        color = AccentCyan
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("DEFAULT LAUNCHER", style = AxisType.Section)
                Spacer(Modifier.height(6.dp))
                Text(
                    "To make AXIS your home screen: Settings → Apps → Default apps → Home app.",
                    style = AxisType.Caption,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
