package axis.app.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import axis.app.data.TransitionStyle
import axis.app.drawer.DrawerScreen
import axis.app.home.HomeScreen
import axis.app.nav.Routes
import axis.app.sidecar.SideCarPanel
import axis.ui.components.PageIndicator
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.Motion
import axis.ui.theme.Scrim
import axis.ui.theme.TextSecondary
import axis.ui.theme.glass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Launcher root (spec Part 4): 2-page pager (Home ↔ Drawer) with the 3D
 * transition, plus the overlay layer (Side-Car in P1; Agent/ Voice HUDs,
 * Chat sheet, confirm gates and kill switch dock here in P3+).
 *
 * Back-handler stack (last registered wins): Side-Car → pager → consume.
 * At root page 0 with no overlay, back intentionally does nothing.
 */
@Composable
fun RootPager(
    onNavigate: (String) -> Unit,
    vm: RootViewModel = hiltViewModel()
) {
    val style by vm.transitionStyle.collectAsStateWithLifecycle()
    val haptics by vm.hapticsEnabled.collectAsStateWithLifecycle()
    val hapticsRef by rememberUpdatedState(haptics)
    val view = LocalView.current
    val density = LocalDensity.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1) // skip the initial emission
            .collect { AxisHaptics.pageSettle(view, hapticsRef) }
    }

    var sidecarOpen by remember { mutableStateOf(false) }
    val sidecarFrac = remember { Animatable(1f) } // 0 = open, 1 = closed
    LaunchedEffect(sidecarOpen) {
        sidecarFrac.animateTo(if (sidecarOpen) 0f else 1f, Motion.DefaultSpring)
    }

    BackHandler(enabled = true) { /* launcher root: back does nothing */ }
    BackHandler(enabled = pagerState.currentPage == 1 && !sidecarOpen) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }
    BackHandler(enabled = sidecarOpen) { sidecarOpen = false }

    AxisBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sidecarOpen, widthPx) {
                        // Right-edge (24dp) swipe-left hot zone → Side-Car.
                        // Anything starting outside the zone is untouched so
                        // the pager keeps every other gesture.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val edgePx = with(density) { 24.dp.toPx() }
                            val slopPx = with(density) { 12.dp.toPx() }
                            if (sidecarOpen || widthPx - down.position.x > edgePx) {
                                return@awaitEachGesture
                            }
                            var acc = 0f
                            var opened = false
                            while (!opened) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change == null || !change.pressed) break
                                acc += change.position.x - change.previousPosition.x
                                if (acc < -slopPx) {
                                    sidecarOpen = true
                                    AxisHaptics.press(view, hapticsRef)
                                    change.consume()
                                    opened = true
                                }
                            }
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                    key = { it }
                ) { page ->
                    PagerPage(
                        style = style,
                        page = page,
                        pagerState = pagerState,
                        onNavigate = onNavigate
                    )
                }
            }

            PageIndicator(
                count = 2,
                selected = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )

            AnimatedVisibility(
                visible = pagerState.currentPage == 0 && sidecarFrac.value > 0.99f,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 72.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .glass(corner = 14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                AxisHaptics.press(view, haptics)
                                sidecarOpen = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = "Open AXIS Core panel",
                        tint = AccentCyan
                    )
                }
            }

            val frac = sidecarFrac.value
            if (frac < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Scrim.copy(alpha = (1f - frac) * 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { sidecarOpen = false }
                        )
                )
                val panelW = minOf(340.dp, maxWidth * 0.88f)
                val panelPx = with(density) { panelW.roundToPx() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset((frac * panelPx).roundToInt(), 0) }
                        .width(panelW)
                        .fillMaxHeight()
                ) {
                    SideCarPanel(
                        shiftPx = frac * panelPx,
                        onClose = { sidecarOpen = false },
                        onNavigate = {
                            sidecarOpen = false
                            onNavigate(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerPage(
    style: TransitionStyle,
    page: Int,
    pagerState: PagerState,
    onNavigate: (String) -> Unit
) {
    val density = LocalDensity.current
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val fraction = offset.absoluteValue.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                cameraDistance = 12f * density.density
                when (style) {
                    TransitionStyle.CUBE -> {
                        rotationY = -30f * offset
                        val s = lerp(1f, 0.88f, fraction)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(1f, 0.45f, fraction)
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (offset < 0) 1f else 0f,
                            pivotFractionY = 0.5f
                        )
                    }
                    // Depth: pages sink + fade symmetrically with |offset|.
                    // (Spec's foreground/background split needs scroll
                    // direction tracking; the symmetric zoom reads the same
                    // and stays direction-agnostic — see KDoc history.)
                    TransitionStyle.DEPTH -> {
                        val s = lerp(1f, 0.85f, fraction)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(1f, 0.4f, fraction)
                        translationZ = -8f * density.density * fraction
                    }
                }
            }
    ) {
        if (page == 0) {
            HomeScreen(onNavigate = onNavigate)
        } else {
            DrawerScreen(onMicClick = { onNavigate(Routes.SETTINGS_VOICE) })
        }
    }
}
