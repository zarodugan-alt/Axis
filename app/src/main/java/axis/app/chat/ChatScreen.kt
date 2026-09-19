package axis.app.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.ui.components.AxisButton
import axis.ui.components.AxisButtonStyle
import axis.ui.components.AxisDialog
import axis.ui.components.GlassCard
import axis.ui.components.GlassChip
import axis.ui.components.StatusPill
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning
import axis.ui.theme.glass

/**
 * Chat / agent surface (spec §S6). Streams the model's answer, shows every
 * tool step inline, and blocks on a confirmation dialog when `:safety`
 * demands one.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    vm: ChatViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val confirm by vm.confirmation.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        ChatHandoff.take()?.let { vm.send(it) }
    }

    LaunchedEffect(state.items.size, state.items.lastOrNull()?.let { it as? ChatItem.Assistant }?.text?.length) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // ------------------------------------------------------- header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AxisSpacing.screen, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AXIS", style = AxisType.Title)
                    Text(
                        if (state.basicMode) "no provider — add a key" else "via ${state.providerLabel}",
                        style = AxisType.Caption,
                        color = if (state.basicMode) Warning else TextSecondary
                    )
                }
                if (state.busy) {
                    GlassChip(label = "stop", icon = Icons.Rounded.Stop, active = true, onClick = vm::stop)
                } else {
                    IconButton(onClick = vm::clear, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Clear chat", tint = TextSecondary)
                    }
                }
            }

            // ----------------------------------------------------- transcript
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AxisSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item.text)
                        is ChatItem.Assistant -> AssistantBubble(item)
                        is ChatItem.Step -> StepRow(item)
                        is ChatItem.Notice -> NoticeRow(item)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // --------------------------------------------------------- input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AxisSpacing.screen, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .glass(corner = 16.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) {
                        Text("Ask AXIS or give it a task…", style = AxisType.Body, color = TextSecondary)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = AxisType.Body.copy(color = TextPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.busy
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (input.isNotBlank() && !state.busy) AccentCyan else TextSecondary
                    )
                }
            }
        }
    }

    // --------------------------------------------------------- confirm gate
    confirm?.let { pending ->
        AxisDialog(
            title = "Confirm action",
            onDismiss = null // blocking: silence must never mean consent
        ) {
            Text(pending.summary, style = AxisType.BodyStrong)
            Spacer(Modifier.height(8.dp))
            Text(pending.reason, style = AxisType.Caption, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                "tool: ${pending.tool} · risk ${pending.risk + 1}/5",
                style = AxisType.Telemetry
            )
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AxisSpacing.screen),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AxisButton(
                    text = "Deny",
                    style = AxisButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.respondToGate(pending.callId, false) }
                )
                AxisButton(
                    text = "Approve",
                    modifier = Modifier.weight(1f),
                    onClick = { vm.respondToGate(pending.callId, true) }
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .glass(corner = 16.dp, borderColor = AccentViolet.copy(alpha = 0.45f))
                .padding(12.dp)
        ) {
            Text(text, style = AxisType.Body)
        }
    }
}

@Composable
private fun AssistantBubble(item: ChatItem.Assistant) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("AXIS", AccentCyan)
            if (item.streaming) {
                Spacer(Modifier.width(8.dp))
                ThinkingDots()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.text.ifEmpty { "…" },
            style = AxisType.Body,
            color = TextPrimary
        )
    }
}

@Composable
private fun StepRow(item: ChatItem.Step) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.label,
            style = AxisType.Telemetry.copy(color = if (item.ok) AccentCyan else Danger)
        )
    }
    Text(
        item.detail,
        style = AxisType.Caption,
        color = TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(corner = 12.dp, borderColor = (if (item.error) Danger else Success).copy(alpha = 0.4f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            item.text,
            style = AxisType.Caption,
            color = if (item.error) Danger else TextSecondary
        )
    }
}

/** Three pulsing dots while tokens arrive. */
@Composable
private fun ThinkingDots() {
    val clock = rememberInfiniteTransition(label = "think")
    val phase by clock.animateFloat(
        0f, 3f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dots"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val active = phase.toInt() % 3 == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .background(
                        if (active) AccentCyan else TextSecondary.copy(alpha = 0.35f),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}
