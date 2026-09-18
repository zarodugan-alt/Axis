package axis.app.settings.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.app.data.TransitionStyle
import axis.app.nav.Routes
import axis.ui.components.SettingsNavRow
import axis.ui.components.SettingsRadioRow
import axis.ui.components.SettingsSliderRow
import axis.ui.components.SettingsSwitchRow
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.TextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {
    val transitionStyle: StateFlow<TransitionStyle> = settings.transitionStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransitionStyle.CUBE)
    val haptics: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val iconSize: StateFlow<Int> = settings.drawerIconSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 48)
    val hiddenCount: StateFlow<Int> = settings.hiddenApps.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setTransition(style: TransitionStyle) =
        viewModelScope.launch { settings.setTransitionStyle(style) }

    fun setHaptics(enabled: Boolean) =
        viewModelScope.launch { settings.setHaptics(enabled) }

    fun setIconSize(dp: Int) =
        viewModelScope.launch { settings.setDrawerIconSize(dp) }
}

/**
 * Appearance settings — fully real in P1: pager transition style, master
 * haptics toggle, drawer icon size, hidden-apps manager.
 */
@Composable
fun AppearanceScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    vm: AppearanceViewModel = hiltViewModel()
) {
    val style by vm.transitionStyle.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val iconSize by vm.iconSize.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    val view = LocalView.current

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
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text("Appearance", style = AxisType.Title)
            }
            Spacer(Modifier.height(8.dp))

            Text("TRANSITION", style = AxisType.Section)
            Spacer(Modifier.height(4.dp))
            SettingsRadioRow(
                title = "Cube",
                subtitle = "3D cube rotation (default)",
                selected = style == TransitionStyle.CUBE,
                onClick = {
                    AxisHaptics.press(view, haptics)
                    vm.setTransition(TransitionStyle.CUBE)
                }
            )
            SettingsRadioRow(
                title = "Depth",
                subtitle = "Sink + fade zoom",
                selected = style == TransitionStyle.DEPTH,
                onClick = {
                    AxisHaptics.press(view, haptics)
                    vm.setTransition(TransitionStyle.DEPTH)
                }
            )

            Spacer(Modifier.height(16.dp))
            Text("FEEL", style = AxisType.Section)
            Spacer(Modifier.height(4.dp))
            SettingsSwitchRow(
                title = "Haptics",
                subtitle = "Touch feedback everywhere",
                checked = haptics,
                onCheckedChange = { vm.setHaptics(it) }
            )
            SettingsSliderRow(
                title = "Drawer icon size",
                value = iconSize.toFloat(),
                onValueChange = { vm.setIconSize(it.toInt()) },
                valueRange = 40f..64f,
                valueLabel = "${iconSize}dp"
            )

            Spacer(Modifier.height(16.dp))
            Text("DRAWER", style = AxisType.Section)
            Spacer(Modifier.height(4.dp))
            SettingsNavRow(
                title = "Hidden apps",
                subtitle = if (hiddenCount == 0) "None hidden" else "$hiddenCount hidden",
                icon = Icons.Rounded.VisibilityOff,
                onClick = {
                    AxisHaptics.press(view, haptics)
                    onNavigate(Routes.HIDDEN_APPS)
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
