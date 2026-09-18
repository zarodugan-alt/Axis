package axis.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.GlassBorder
import axis.ui.theme.TextSecondary
import axis.ui.theme.TextTertiary
import axis.ui.theme.glass
import axis.ui.theme.glow

/**
 * Glass search field (home, drawer, chat jump). Focused state brightens the
 * border to cyan + halo. Mic button is optional (drawer keeps it, some
 * embedded uses don't).
 */
@Composable
fun AxisSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Ask or search…",
    onMicClick: (() -> Unit)? = null,
    onSearch: (String) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        if (focused) AccentCyan.copy(alpha = 0.6f) else GlassBorder,
        label = "border"
    )
    var barMod = modifier
        .fillMaxWidth()
        .height(56.dp)
        .glass(corner = AxisRadii.field, borderColor = border)
    if (focused) barMod = barMod.glow(AccentCyan, radius = 16.dp)

    Row(
        modifier = barMod.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(22.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .onFocusChanged { focused = it.isFocused },
            textStyle = AxisType.Body,
            cursorBrush = SolidColor(AccentCyan),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(hint, style = AxisType.Body.copy(color = TextTertiary))
                }
                inner()
            }
        )
        if (onMicClick != null) {
            IconButton(onClick = onMicClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice input",
                    tint = if (focused) AccentCyan else TextSecondary
                )
            }
        }
    }
}

@Preview
@Composable
private fun AxisSearchBarPreview() {
    AxisTheme {
        var q by remember { mutableStateOf("") }
        AxisSearchBar(query = q, onQueryChange = { q = it }, onMicClick = {})
    }
}
