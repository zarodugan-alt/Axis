package axis.app.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.AxisOrb
import axis.ui.components.GlassCard
import axis.ui.components.OrbState
import axis.ui.components.StatusPill
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Success
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning

/**
 * First-run setup (spec §S1): the four things AXIS actually needs, each with
 * a real system link — default home app, notification access, accessibility
 * (optional) and one API key. Every step is skippable; AXIS runs in basic
 * mode with none of them.
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onOpenProviders: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micAsked by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional — voice still works if the user declines */ }

    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AxisSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            AxisOrb(state = OrbState.IDLE, orbSize = 104.dp)
            Spacer(Modifier.height(16.dp))
            Text("Welcome to AXIS", style = AxisType.Title)
            Text(
                "A local-first launcher with an AI core. Nothing leaves this device " +
                    "unless you wire a provider.",
                style = AxisType.Caption,
                color = TextSecondary
            )

            Spacer(Modifier.height(AxisSpacing.section))

            // 1 — home app
            SetupStep(
                index = 1,
                title = "Set AXIS as your home app",
                detail = "Required for AXIS to replace your current launcher.",
                done = state.isDefaultLauncher,
                actionLabel = "Open home settings",
                onAction = vm::openHomeSettings
            )

            Spacer(Modifier.height(AxisSpacing.cardGap))

            // 2 — notifications
            SetupStep(
                index = 2,
                title = "Notification access",
                detail = "Lets AXIS hold, triage and clear notifications. Optional.",
                done = state.notificationAccess,
                actionLabel = "Grant access",
                onAction = vm::openNotificationAccess
            )

            Spacer(Modifier.height(AxisSpacing.cardGap))

            // 3 — accessibility
            SetupStep(
                index = 3,
                title = "Accessibility service",
                detail = "Gives AXIS context about the app in front. Optional and revocable.",
                done = state.accessibility,
                actionLabel = "Enable service",
                onAction = vm::openAccessibilitySettings
            )

            Spacer(Modifier.height(AxisSpacing.cardGap))

            // 4 — microphone
            SetupStep(
                index = 4,
                title = "Microphone",
                detail = "For voice input on the home board. Optional.",
                done = state.micGranted,
                actionLabel = "Allow microphone",
                onAction = {
                    micAsked = true
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            Spacer(Modifier.height(AxisSpacing.cardGap))

            // 5 — provider
            SetupStep(
                index = 5,
                title = "Connect an AI provider",
                detail = "Paste a key (Groq has a free tier) to switch AXIS from " +
                    "launcher-only to assistant.",
                done = state.providersConnected > 0,
                actionLabel = "Add a key",
                onAction = onOpenProviders
            )

            Spacer(Modifier.height(AxisSpacing.section))
            AxisButton(
                text = if (state.providersConnected > 0) "Enter AXIS" else "Skip for now",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.finish(onContinue)
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "You can re-run this setup any time from Settings → About.",
                style = AxisType.Caption,
                color = TextSecondary
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SetupStep(
    index: Int,
    title: String,
    detail: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (done) axis.ui.components.GlassAccent.CYAN else axis.ui.components.GlassAccent.NONE
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index),
                style = AxisType.Telemetry.copy(color = if (done) Success else AccentCyan)
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = AxisType.BodyStrong, modifier = Modifier.weight(1f))
            StatusPill(
                text = if (done) "DONE" else "OPTIONAL",
                color = if (done) Success else Warning
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(detail, style = AxisType.Caption, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        AxisButton(
            text = if (done) "Re-open settings" else actionLabel,
            style = AxisButtonStyle.SECONDARY,
            onClick = onAction
        )
    }
}
