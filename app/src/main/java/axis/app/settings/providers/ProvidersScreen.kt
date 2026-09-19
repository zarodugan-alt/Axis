package axis.app.settings.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.agent.provider.ProviderCatalog.RoutingMode
import axis.app.data.ProviderConfig
import axis.app.nav.Routes
import axis.ui.components.DotStatus
import axis.ui.components.GlassCard
import axis.ui.components.SettingsRadioRow
import axis.ui.components.StatusDot
import axis.ui.components.StatusPill
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning

/**
 * Settings → AI Providers (spec §S10 · §F5). This is where BYOK happens:
 * paste a key, run a real test call, pick the model, and choose how the
 * gateway routes between the providers that are switched on.
 */
@Composable
fun ProvidersScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    vm: ProvidersViewModel = hiltViewModel()
) {
    val chat by vm.chatProviders.collectAsStateWithLifecycle()
    val speech by vm.speechProviders.collectAsStateWithLifecycle()
    val mode by vm.routingMode.collectAsStateWithLifecycle()
    val preferred by vm.preferredId.collectAsStateWithLifecycle()
    val connected by vm.connectedCount.collectAsStateWithLifecycle()
    val test by vm.testState.collectAsStateWithLifecycle()
    val view = LocalView.current

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
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Providers", style = AxisType.Title)
                    Text(
                        if (connected == 0) "No provider connected — basic mode"
                        else "$connected connected · BYOK, stored encrypted",
                        style = AxisType.Caption
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))

            // ------------------------------------------------------- routing
            Text("ROUTING", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsRadioRow(
                    title = "Auto (failover)",
                    subtitle = "Try providers in order until one answers",
                    selected = mode == RoutingMode.AUTO,
                    onClick = { vm.setRoutingMode(RoutingMode.AUTO) }
                )
                SettingsRadioRow(
                    title = "Manual (single)",
                    subtitle = "Only use the preferred provider",
                    selected = mode == RoutingMode.MANUAL,
                    onClick = { vm.setRoutingMode(RoutingMode.MANUAL) }
                )
                SettingsRadioRow(
                    title = "Cheap first",
                    subtitle = "Free-tier providers before paid ones",
                    selected = mode == RoutingMode.CHEAP_FIRST,
                    onClick = { vm.setRoutingMode(RoutingMode.CHEAP_FIRST) }
                )
            }

            Spacer(Modifier.height(AxisSpacing.section))

            // ------------------------------------------------------ providers
            Text("CHAT PROVIDERS", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            chat.forEach { config ->
                ProviderRow(
                    config = config,
                    preferred = preferred == config.provider.id,
                    onClick = {
                        AxisHaptics.press(view)
                        onNavigate(Routes.providerDetail(config.provider.id))
                    }
                )
                Spacer(Modifier.height(AxisSpacing.cardGap))
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(Routes.providerDetail("custom")) }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Add custom endpoint", style = AxisType.BodyStrong)
                    Text(
                        "Any OpenAI-compatible server (LAN, vLLM, LM Studio)",
                        style = AxisType.Caption,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(AxisSpacing.section))

            // --------------------------------------------------------- voice
            Text("VOICE OUTPUT", style = AxisType.Section)
            Spacer(Modifier.height(4.dp))
            Text(
                "Used by the voice HUD. Falls back to the on-device engine when " +
                    "no speech key is present.",
                style = AxisType.Caption,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            speech.forEach { config ->
                ProviderRow(
                    config = config,
                    preferred = false,
                    onClick = { onNavigate(Routes.providerDetail(config.provider.id)) }
                )
                Spacer(Modifier.height(AxisSpacing.cardGap))
            }

            if (test != null) {
                Spacer(Modifier.height(AxisSpacing.section))
                val t = test!!
                val color = when (t.ok) {
                    true -> Success
                    false -> Danger
                    null -> TextSecondary
                }
                Text(
                    if (t.running) "Testing ${t.providerId}…" else t.message,
                    style = AxisType.Caption,
                    color = color
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderRow(
    config: ProviderConfig,
    preferred: Boolean,
    onClick: () -> Unit
) {
    val p = config.provider
    val status = when {
        config.hasKey && config.keyValid == true -> DotStatus.ON
        config.hasKey && config.keyValid == false -> DotStatus.DEGRADED
        p.keyless && config.enabled -> DotStatus.ON
        else -> DotStatus.OFF
    }
    val pill: Pair<String, androidx.compose.ui.graphics.Color> = when {
        config.hasKey && config.keyValid == true -> "READY" to Success
        config.hasKey && config.keyValid == false -> "KEY FAILED" to Danger
        p.keyless -> "NO KEY NEEDED" to AccentCyan
        config.hasKey -> "UNTESTED" to Warning
        else -> "ADD KEY" to TextSecondary
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, style = AxisType.BodyStrong)
                    if (preferred) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill("PREFERRED", AccentCyan)
                    }
                }
                Text(
                    buildString {
                        append(config.modelOrDefault)
                        p.freeTier?.let { append(" · ").append(it) }
                    },
                    style = AxisType.Caption,
                    color = TextSecondary
                )
                if (config.hasKey && config.keyPreview != null) {
                    Text(config.keyPreview, style = AxisType.Telemetry)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(pill.first, pill.second)
        }
        if (p.blurb.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(p.blurb, style = AxisType.Caption, color = TextSecondary)
        }
    }
}
