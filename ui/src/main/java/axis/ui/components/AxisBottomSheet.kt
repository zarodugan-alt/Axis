package axis.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.Scrim
import axis.ui.theme.TextTertiary
import axis.ui.theme.glassSolid

/**
 * Custom modal bottom sheet (28dp top corners, drag handle, solid glass):
 * scrim tap / back button dismiss. Spring rise per the motion system.
 * (Drag-to-dismiss arrives as P2 polish; explicit dismissal only in P1.)
 */
@Composable
fun AxisBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onDismiss = onDismiss)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = Motion.DefaultSpringIntOffset,
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    animationSpec = Motion.DefaultSpringIntOffset,
                    targetOffsetY = { it }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSolid(corner = AxisRadii.sheetTop)
                        .padding(
                            start = AxisSpacing.screen,
                            end = AxisSpacing.screen,
                            bottom = AxisSpacing.screen
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(TextTertiary.copy(alpha = 0.6f))
                            .align(Alignment.CenterHorizontally)
                    )
                    content()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview
@Composable
private fun AxisBottomSheetPreview() {
    AxisTheme {
        AxisBottomSheet(onDismiss = {}) {
            Text("Sheet content", style = AxisType.Title)
        }
    }
}
