package axis.app.home

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.app.nav.Routes
import axis.kernel.model.AppEntry
import axis.ui.components.AppIcon
import axis.ui.components.AxisBottomSheet
import axis.ui.components.AxisOrb
import axis.ui.components.AxisSearchBar
import axis.ui.components.GlassCard
import axis.ui.components.OrbState
import axis.ui.components.PriorityCard
import axis.ui.components.SettingsNavRow
import axis.ui.components.SuggestionRow
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Home (spec §S2): orb + greeting + search + NEXT UP + SUGGESTED.
 * P2 wires pull-down→shade and double-tap→lock (accessibility globals);
 * P3 connects the orb tap to Chat (Basic Mode routes to Providers instead).
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val greeting by vm.greeting.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val suggested by vm.suggested.collectAsStateWithLifecycle()
    val priority by vm.priority.collectAsStateWithLifecycle()
    val basicMode by vm.basicMode.collectAsStateWithLifecycle()
    val haptics by vm.hapticsEnabled.collectAsStateWithLifecycle()
    val view = LocalView.current
    var settingsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AxisSpacing.screen)
    ) {
        Spacer(Modifier.height(24.dp))

        // Orb: tap → Chat (or Providers in Basic Mode), long-press → Voice.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .pointerInput(basicMode) {
                    detectTapGestures(
                        onTap = {
                            AxisHaptics.press(view, haptics)
                            onNavigate(if (basicMode) Routes.SETTINGS_PROVIDERS else Routes.CHAT)
                        },
                        onLongPress = {
                            AxisHaptics.longPress(view, haptics)
                            onNavigate(Routes.SETTINGS_VOICE)
                        }
                    )
                }
        ) {
            AxisOrb(
                state = OrbState.IDLE,
                orbSize = 120.dp,
                modifier = Modifier.alpha(if (basicMode) 0.6f else 1f)
            )
        }
        if (basicMode) {
            Text(
                text = "AI not set up — tap the orb to connect",
                style = AxisType.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigate(Routes.SETTINGS_PROVIDERS) }
                    )
                    .padding(vertical = 4.dp)
            )
        }

        // Greeting doubles as a long-press empty-zone (home settings).
        Text(
            text = greeting,
            style = AxisType.Display,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        AxisHaptics.longPress(view, haptics)
                        settingsSheet = true
                    })
                }
                .padding(vertical = 8.dp)
        )

        Spacer(Modifier.height(8.dp))
        AxisSearchBar(
            query = query,
            onQueryChange = vm::setQuery,
            hint = if (basicMode) "Search apps…" else "Ask or search…",
            onMicClick = { onNavigate(Routes.SETTINGS_VOICE) },
            onSearch = { q ->
                // P3: route to chat/agent; P1 launches a sole exact match.
                val exact = results.firstOrNull { it.label.equals(q.trim(), ignoreCase = true) }
                if (exact != null) vm.launch(exact.packageName)
            }
        )

        if (query.isBlank()) {
            if (!priority.isEmpty) {
                Spacer(Modifier.height(AxisSpacing.section))
                Text("NEXT UP", style = AxisType.Section)
                Spacer(Modifier.height(8.dp))
                PriorityCard(data = priority)
            }
            Spacer(Modifier.height(AxisSpacing.section))
            Text("SUGGESTED", style = AxisType.Section)
            Spacer(Modifier.height(12.dp))
            SuggestionRow(
                state = suggested,
                onAppClick = {
                    AxisHaptics.press(view, haptics)
                    vm.launch(it)
                }
            )
        } else {
            Spacer(Modifier.height(AxisSpacing.cardGap))
            SearchResultsCard(
                results = results,
                query = query,
                iconFor = vm::iconFor,
                onAppClick = {
                    AxisHaptics.press(view, haptics)
                    vm.launch(it)
                },
                onWebSearch = {
                    AxisHaptics.press(view, haptics)
                    vm.webSearch(query)
                }
            )
        }

        // Bottom empty-zone: long-press → home settings. Also clears dots.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        AxisHaptics.longPress(view, haptics)
                        settingsSheet = true
                    })
                }
        )
    }

    if (settingsSheet) {
        AxisBottomSheet(onDismiss = { settingsSheet = false }) {
            Text("Home", style = AxisType.Title)
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                title = "Wallpaper",
                subtitle = "System picker",
                icon = Icons.Rounded.Image,
                onClick = {
                    settingsSheet = false
                    vm.openWallpaperPicker()
                }
            )
            SettingsNavRow(
                title = "Widgets",
                subtitle = "Arrives in Phase 4",
                icon = Icons.Rounded.Widgets,
                onClick = { /* P4 — row shows the plan, does nothing yet */ }
            )
            SettingsNavRow(
                title = "Appearance",
                subtitle = "Transition, haptics, icons",
                icon = Icons.Rounded.Palette,
                onClick = {
                    settingsSheet = false
                    onNavigate(Routes.SETTINGS_APPEARANCE)
                }
            )
        }
    }
}

@Composable
private fun SearchResultsCard(
    results: List<AppEntry>,
    query: String,
    iconFor: (String) -> Drawable?,
    onAppClick: (String) -> Unit,
    onWebSearch: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        if (results.isEmpty()) {
            Text(
                "No apps match “${query.trim()}”",
                style = AxisType.Body,
                color = TextSecondary
            )
        } else {
            results.forEachIndexed { index, app ->
                SearchResultRow(
                    app = app,
                    index = index,
                    icon = iconFor(app.packageName),
                    onClick = { onAppClick(app.packageName) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onWebSearch
                )
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary)
            Text(
                "Search web for “${query.trim()}”",
                style = AxisType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    app: AppEntry,
    index: Int,
    icon: Drawable?,
    onClick: () -> Unit
) {
    val enter = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(app.packageName) {
        delay((index * Motion.STAGGER_MS).toLong())
        enter.animateTo(1f, tween(200))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * with(density) { 24.dp.toPx() }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        AppIcon(icon = icon, label = app.label, size = 40.dp)
        Text(
            app.label,
            style = AxisType.BodyStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
