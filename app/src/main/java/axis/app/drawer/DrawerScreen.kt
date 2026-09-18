package axis.app.drawer

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.kernel.model.AppEntry
import axis.ui.components.AppIcon
import axis.ui.components.AxisBottomSheet
import axis.ui.components.AxisSearchBar
import axis.ui.components.SettingsNavRow
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.TextSecondary
import axis.ui.theme.glassSolid
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App drawer (spec §S3): 4-column grid, fuzzy search, letter scrubber with
 * haptic ticks + floating bubble, long-press action sheet. Notification
 * badges and "Add to Home" arrive in P2/P3 (see KDocs).
 */
@Composable
fun DrawerScreen(
    onMicClick: () -> Unit,
    vm: DrawerViewModel = hiltViewModel()
) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val iconSize by vm.iconSize.collectAsStateWithLifecycle()
    val haptics by vm.hapticsEnabled.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var sheetApp by remember { mutableStateOf<AppEntry?>(null) }
    var scrubLetter by remember { mutableStateOf<String?>(null) }
    var lastTicked by remember { mutableStateOf<String?>(null) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }

    val letters = remember(apps) { apps.map { letterOf(it.label) }.distinct() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AxisSpacing.screen)
        ) {
            Spacer(Modifier.height(12.dp))
            AxisSearchBar(
                query = query,
                onQueryChange = vm::setQuery,
                hint = "Search apps…",
                onMicClick = onMicClick
            )
            Spacer(Modifier.height(AxisSpacing.cardGap))
            if (apps.isEmpty() && query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps match “$query”",
                        style = AxisType.Body,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 88.dp, // clears the pager dots overlay
                        end = 24.dp // clears the letter scrubber
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        DrawerCell(
                            app = app,
                            index = index,
                            icon = vm.iconFor(app.packageName),
                            iconSizeDp = iconSize,
                            badge = vm.badgeCount(app.packageName),
                            onClick = {
                                AxisHaptics.press(view, haptics)
                                vm.launch(app.packageName)
                            },
                            onLongClick = {
                                AxisHaptics.longPress(view, haptics)
                                sheetApp = app
                            }
                        )
                    }
                }
            }
        }

        if (letters.isNotEmpty() && query.isBlank()) {
            LetterScrubber(
                letters = letters,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                onScrub = { letter, scrubbing ->
                    scrubLetter = letter?.takeIf { scrubbing }
                    if (scrubbing && letter != null) {
                        val idx = apps.indexOfFirst { letterOf(it.label) == letter }
                        if (idx >= 0) {
                            scrubJob?.cancel()
                            scrubJob = scope.launch { gridState.animateScrollToItem(idx) }
                        }
                        if (letter != lastTicked) {
                            AxisHaptics.tick(view, haptics)
                            lastTicked = letter
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = scrubLetter != null,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(animationSpec = Motion.BounceSpring, initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(animationSpec = Motion.BounceSpring) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .glassSolid(corner = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(scrubLetter ?: "", style = AxisType.Title)
            }
        }

        sheetApp?.let { app ->
            AppActionSheet(
                app = app,
                icon = vm.iconFor(app.packageName),
                onDismiss = { sheetApp = null },
                onOpen = {
                    AxisHaptics.press(view, haptics)
                    sheetApp = null
                    vm.launch(app.packageName)
                },
                onAppInfo = {
                    AxisHaptics.press(view, haptics)
                    sheetApp = null
                    vm.openAppInfo(app.packageName)
                },
                onHide = {
                    AxisHaptics.press(view, haptics)
                    sheetApp = null
                    vm.hide(app.packageName)
                    Toast.makeText(context, "Hidden — manage in Appearance", Toast.LENGTH_SHORT).show()
                },
                onUninstall = {
                    AxisHaptics.press(view, haptics)
                    sheetApp = null
                    vm.uninstall(app.packageName)
                }
            )
        }
    }
}

@Composable
private fun DrawerCell(
    app: AppEntry,
    index: Int,
    icon: Drawable?,
    iconSizeDp: Int,
    badge: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // List-item entry: fade + 24dp rise, staggered 30ms (spec §3.3).
    val enter = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(app.packageName) {
        delay(((index % 20) * Motion.STAGGER_MS).toLong())
        enter.animateTo(1f, tween(200))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * with(density) { 24.dp.toPx() }
        }
    ) {
        AppIcon(
            icon = icon,
            label = app.label,
            size = iconSizeDp.dp,
            badgeCount = badge,
            isNew = app.isNew,
            onClick = onClick,
            onLongClick = onLongClick
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = AxisType.Caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 32.dp)
        )
    }
}

@Composable
private fun LetterScrubber(
    letters: List<String>,
    modifier: Modifier = Modifier,
    onScrub: (letter: String?, scrubbing: Boolean) -> Unit
) {
    var heightPx by remember { mutableIntStateOf(0) }
    val currentLetters by rememberUpdatedState(letters)
    val callback by rememberUpdatedState(onScrub)
    Column(
        modifier = modifier
            .onSizeChanged { heightPx = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val h = heightPx
                    if (h <= 0 || currentLetters.isEmpty()) return@awaitEachGesture
                    fun letterAt(y: Float): String {
                        val idx = ((y / h) * currentLetters.size).toInt()
                            .coerceIn(0, currentLetters.size - 1)
                        return currentLetters[idx]
                    }
                    callback(letterAt(down.position.y), true)
                    var done = false
                    while (!done) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            done = true
                        } else {
                            callback(letterAt(change.position.y), true)
                            change.consume()
                        }
                    }
                    callback(null, false)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                style = AxisType.Caption.copy(color = TextSecondary),
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

/**
 * Long-press actions. "Add to Home" arrives with home favorites (P2) — the
 * four shipping actions are Open / App info / Hide / Uninstall.
 */
@Composable
private fun AppActionSheet(
    app: AppEntry,
    icon: Drawable?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit
) {
    AxisBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon = icon, label = app.label, size = 48.dp)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(app.label, style = AxisType.Title, maxLines = 1)
                Text(app.packageName, style = AxisType.Caption, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsNavRow("Open", onClick = onOpen, icon = Icons.Rounded.OpenInNew)
        SettingsNavRow("App info", onClick = onAppInfo, icon = Icons.Rounded.Info)
        SettingsNavRow("Hide", onClick = onHide, icon = Icons.Rounded.VisibilityOff)
        SettingsNavRow("Uninstall", onClick = onUninstall, icon = Icons.Rounded.Delete)
    }
}

private fun letterOf(label: String): String {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c.toString() else "#"
}
