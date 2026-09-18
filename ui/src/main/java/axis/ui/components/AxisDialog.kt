package axis.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.Scrim
import axis.ui.theme.glassSolid

/**
 * Glass dialog (spec §3.3): scale 0.92→1 + fade on [Motion.BounceSpring].
 * Solid glass for legibility. [onDismiss] null = blocking (no scrim/back
 * dismiss — used for P3 confirmation gates that must be answered).
 */
@Composable
fun AxisDialog(
    title: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null
) {
    if (onDismiss != null) {
        BackHandler(onBack = onDismiss)
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .then(
                    if (onDismiss != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                    } else Modifier
                )
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(animationSpec = Motion.BounceSpring, initialScale = 0.92f) + fadeIn(),
                exit = scaleOut(animationSpec = Motion.BounceSpring) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AxisSpacing.screen)
                        .glassSolid()
                        .padding(AxisSpacing.screen)
                ) {
                    Text(title, style = AxisType.Title)
                    if (body != null) {
                        Spacer(Modifier.height(12.dp))
                        body()
                    }
                    if (buttons != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AxisDialogPreview() {
    AxisTheme {
        AxisDialog(title = "Send message?", onDismiss = {}, body = {
            Text("This is how P3 confirmation gates will look.", style = AxisType.Body)
        })
    }
}
