package axis.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType

sealed interface SuggestionState {
    data object Loading : SuggestionState
    data class Loaded(val items: List<SuggestionItem>) : SuggestionState
}

/**
 * Home "SUGGESTED" row: 5 predicted apps (icon 48dp + one-line label).
 * Loading state shows shimmer placeholders with identical geometry so the
 * layout never jumps when predictions arrive.
 */
@Composable
fun SuggestionRow(
    state: SuggestionState,
    onAppClick: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (state) {
            is SuggestionState.Loading -> repeat(5) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    ShimmerBox(Modifier.size(48.dp), shape = CircleShape)
                    ShimmerBox(
                        Modifier
                            .width(48.dp)
                            .height(12.dp)
                    )
                }
            }
            is SuggestionState.Loaded -> state.items.take(5).forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAppClick(item.packageName) }
                        )
                ) {
                    AppIcon(icon = item.icon, label = item.label)
                    Text(
                        text = item.label,
                        style = AxisType.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SuggestionRowLoadingPreview() {
    AxisTheme { SuggestionRow(SuggestionState.Loading, onAppClick = {}) }
}
