package axis.app.settings.providers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.agent.keys.KeyValidator
import axis.app.data.ProviderConfig
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.GlassCard
import axis.ui.components.KeyField
import axis.ui.components.KeyFieldStatus
import axis.ui.components.SettingsSwitchRow
import axis.ui.components.StatusPill
import axis.ui.components.StoredSecretRow
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary

/**
 * One provider's setup page: paste/replace the key, run a live test, pick the
 * model, and switch the provider on. Also hosts the custom-endpoint form.
 */
@Composable
fun ProviderDetailScreen(
    providerId: String?,
    onBack: () -> Unit,
    vm: ProvidersViewModel = hiltViewModel()
) {
    val id = providerId ?: "custom"
    val isCustom = id == "custom"

    val config by vm.config(id).collectAsStateWithLifecycle()
    val preferred by vm.preferredId.collectAsStateWithLifecycle()
    val speechId by vm.speechProviderId.collectAsStateWithLifecycle()
    val test by vm.testState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var keyInput by remember(id) { mutableStateOf("") }
    var modelInput by remember(id) { mutableStateOf("") }
    var customLabel by remember { mutableStateOf("Custom endpoint") }
    var customBase by remember { mutableStateOf("http://192.168.1.10:8000/v1") }
    var replacing by remember(id) { mutableStateOf(false) }

    LaunchedEffect(config?.provider?.id) {
        config?.let {
            modelInput = it.modelOrDefault
            if (isCustom) {
                customLabel = it.provider.label
                customBase = it.provider.baseUrl
            }
        }
    }

    val cfg: ProviderConfig? = config

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
                Text(cfg?.provider?.label ?: "Provider", style = AxisType.Title)
            }

            if (cfg == null) {
                Spacer(Modifier.height(24.dp))
                Text("Loading provider…", style = AxisType.Body, color = TextSecondary)
                return@Column
            }

            Spacer(Modifier.height(4.dp))
            if (cfg.provider.blurb.isNotBlank()) {
                Text(cfg.provider.blurb, style = AxisType.Caption, color = TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            if (cfg.provider.freeTier != null) {
                StatusPill(cfg.provider.freeTier, Success)
            }

            // ------------------------------------------------ custom endpoint
            if (isCustom) {
                Spacer(Modifier.height(AxisSpacing.section))
                Text("ENDPOINT", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedInput(
                        label = "Name",
                        value = customLabel,
                        onValueChange = { customLabel = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedInput(
                        label = "Base URL",
                        value = customBase,
                        onValueChange = { customBase = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Must expose /chat/completions (OpenAI-compatible). " +
                            "Cleartext http:// works for LAN servers.",
                        style = AxisType.Caption,
                        color = TextSecondary
                    )
                }
            }

            // ------------------------------------------------------ api key
            Spacer(Modifier.height(AxisSpacing.section))
            Text("API KEY", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))

            if (cfg.hasKey && !replacing) {
                StoredSecretRow(
                    preview = cfg.keyPreview.orEmpty(),
                    statusText = when (cfg.keyValid) {
                        true -> "Verified ${timeAgo(cfg.lastCheckedAt)}"
                        false -> cfg.lastError?.take(90) ?: "Last test failed"
                        null -> "Stored — not tested yet"
                    },
                    statusOk = cfg.keyValid,
                    trailing = {
                        AxisButton(
                            text = "Replace",
                            style = AxisButtonStyle.SECONDARY,
                            onClick = { replacing = true }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text("Stored encrypted (AES-GCM, Android Keystore).", style = AxisType.Caption, color = TextSecondary)
            } else {
                val check = if (keyInput.isBlank()) null else KeyValidator.validate(cfg.provider, keyInput)
                KeyField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = if (isCustom) "API key (blank if the server has none)" else "Paste your ${cfg.provider.label} key",
                    status = when (check?.ok) {
                        true -> KeyFieldStatus.VALID
                        false -> KeyFieldStatus.INVALID
                        null -> KeyFieldStatus.NEUTRAL
                    },
                    helper = check?.message
                )
                Spacer(Modifier.height(12.dp))
                AxisButton(
                    text = if (isCustom) "Save endpoint" else "Save key",
                    enabled = keyInput.isNotBlank() || (isCustom && customBase.isNotBlank()),
                    onClick = {
                        AxisHaptics.press(view)
                        if (isCustom) {
                            vm.saveCustom(customLabel, customBase, modelInput.ifBlank { "default" }, keyInput)
                        } else {
                            vm.saveKey(id, keyInput)
                        }
                        keyInput = ""
                        replacing = false
                    }
                )
            }

            if (cfg.provider.keyHelpUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(cfg.provider.keyHelpUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = AccentCyan)
                    Spacer(Modifier.width(12.dp))
                    Text("Get a ${cfg.provider.label} key", style = AxisType.Body, color = AccentCyan)
                }
            }

            // --------------------------------------------------- test + state
            Spacer(Modifier.height(AxisSpacing.section))
            Text("VERIFY", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            AxisButton(
                text = if (test?.running == true) "Testing…" else "Test connection",
                enabled = cfg.hasKey || cfg.provider.keyless,
                style = AxisButtonStyle.SECONDARY,
                onClick = {
                    AxisHaptics.press(view)
                    vm.testKey(id)
                }
            )
            val t = test?.takeIf { it.providerId == id }
            if (t != null && !t.running && t.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val color: Color = when (t.ok) {
                    true -> Success
                    false -> Danger
                    null -> TextSecondary
                }
                Text(t.message, style = AxisType.Telemetry.copy(color = color))
            }
            if (cfg.hasKey) {
                Spacer(Modifier.height(8.dp))
                AxisButton(
                    text = "Remove key",
                    style = AxisButtonStyle.DANGER,
                    onClick = {
                        vm.clearKey(id)
                        replacing = false
                    }
                )
            }

            // -------------------------------------------------------- model
            Spacer(Modifier.height(AxisSpacing.section))
            Text("MODEL", style = AxisType.Section)
            Spacer(Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                cfg.provider.models.forEach { model ->
                    ModelRow(
                        model = model,
                        selected = modelInput == model,
                        onSelect = {
                            modelInput = model
                            vm.setModel(id, model)
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedInput(
                label = "Or type a model id",
                value = modelInput,
                onValueChange = {
                    modelInput = it
                    vm.setModel(id, it)
                }
            )

            // ------------------------------------------------------- enable
            Spacer(Modifier.height(AxisSpacing.section))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SettingsSwitchRow(
                    title = "Use this provider",
                    subtitle = if (cfg.ready) "Will be used for agent requests" else "Connect a key first",
                    checked = cfg.enabled,
                    onCheckedChange = { vm.setEnabled(id, it) }
                )
                if (cfg.provider.isChat) {
                    SettingsSwitchRow(
                        title = "Prefer for chat",
                        subtitle = "Try this provider first (routing: auto)",
                        checked = preferred == id,
                        onCheckedChange = { on -> vm.setPreferred(if (on) id else null) }
                    )
                } else {
                    SettingsSwitchRow(
                        title = "Use for voice",
                        subtitle = "Speak agent replies with this voice",
                        checked = speechId == id,
                        onCheckedChange = { on -> vm.setSpeechProvider(if (on) id else null) }
                    )
                }
            }

            if (isCustom && cfg.provider.baseUrl != axis.agent.provider.ProviderCatalog.custom.baseUrl) {
                Spacer(Modifier.height(16.dp))
                AxisButton(
                    text = "Remove custom endpoint",
                    style = AxisButtonStyle.DANGER,
                    onClick = { vm.clearCustom(); onBack() }
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ModelRow(model: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) AccentCyan else TextSecondary
        )
        Spacer(Modifier.width(12.dp))
        Text(model, style = AxisType.Telemetry.copy(color = if (selected) TextPrimary else TextSecondary))
    }
}

@Composable
private fun OutlinedInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AxisType.Caption)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AxisType.Telemetry.copy(color = TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (focused) AccentCyan.copy(alpha = 0.06f) else Color.Transparent
                )
                .padding(vertical = 10.dp)
        )
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (focused) AccentCyan.copy(alpha = 0.5f) else Color(0x33FFFFFF))
        )
    }
}

/** "3 min ago" style label; falls back to "never" for a zero timestamp. */
private fun timeAgo(ts: Long): String {
    if (ts <= 0L) return "never"
    val delta = System.currentTimeMillis() - ts
    return when {
        delta < 60_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000} min ago"
        delta < 86_400_000 -> "${delta / 3_600_000} h ago"
        else -> "${delta / 86_400_000} d ago"
    }
}
