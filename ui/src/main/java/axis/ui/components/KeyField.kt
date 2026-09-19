package axis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.Danger
import axis.ui.theme.GlassBorder
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.AxisType
import axis.ui.theme.glass

enum class KeyFieldStatus { NEUTRAL, VALID, INVALID }

/**
 * Masked secret field for API keys. Everything stays on-device: the paste
 * button reads the system clipboard directly, the value is masked by default
 * and the component never logs or persists it (the caller stores it through
 * the encrypted vault).
 */
@Composable
fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "API key",
    enabled: Boolean = true,
    status: KeyFieldStatus = KeyFieldStatus.NEUTRAL,
    helper: String? = null
) {
    var revealed by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val border = when (status) {
        KeyFieldStatus.VALID -> Success.copy(alpha = 0.6f)
        KeyFieldStatus.INVALID -> Danger.copy(alpha = 0.6f)
        KeyFieldStatus.NEUTRAL -> GlassBorder
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = AxisType.Caption)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .glass(corner = 16.dp, borderColor = border)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Paste or type the key", style = AxisType.Body, color = TextSecondary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = AxisType.Telemetry.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AccentCyan),
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                    visualTransformation = if (revealed) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = { revealed = !revealed },
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) "Hide key" else "Show key",
                    tint = TextSecondary
                )
            }
            IconButton(
                onClick = {
                    clipboard.getText()?.text?.let { onValueChange(it.trim()) }
                },
                enabled = enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste from clipboard", tint = TextSecondary)
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    enabled = enabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear field", tint = TextSecondary)
                }
            }
        }
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                helper,
                style = AxisType.Caption,
                color = when (status) {
                    KeyFieldStatus.VALID -> Success
                    KeyFieldStatus.INVALID -> Danger
                    KeyFieldStatus.NEUTRAL -> TextSecondary
                }
            )
        }
    }
}

/** Non-editable display of a stored secret (already masked by the caller). */
@Composable
fun StoredSecretRow(
    preview: String,
    statusText: String,
    statusOk: Boolean?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val color = when (statusOk) {
        true -> Success
        false -> Danger
        null -> TextSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(corner = 16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(preview.ifEmpty { "—" }, style = AxisType.Telemetry.copy(color = TextPrimary))
            Spacer(Modifier.height(2.dp))
            Text(statusText, style = AxisType.Caption, color = color)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Faint status pill used in provider rows (READY / NO KEY / FAILED). */
@Composable
fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glass(corner = 50.dp, borderColor = color.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AxisType.Caption.copy(color = color))
    }
}
