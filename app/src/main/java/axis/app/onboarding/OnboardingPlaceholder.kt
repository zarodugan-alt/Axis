package axis.app.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.ui.components.AxisButton
import axis.ui.components.ComingSoon
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}

/**
 * Stand-in for the P2 setup wizard (spec §S1): 8 tappable steps with
 * screenshots, haptics and auto-advance. Until then, one honest button.
 */
@Composable
fun OnboardingPlaceholder(
    onContinue: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val view = LocalView.current
    AxisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AxisSpacing.screen)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ComingSoon(
                    phase = "Phase 2",
                    title = "Setup wizard",
                    description = "8 guided steps — accessibility, notifications, overlay, " +
                        "battery, launcher — all tappable, all grandma-proof."
                )
            }
            Spacer(Modifier.height(16.dp))
            AxisButton(
                text = "Continue to Home",
                onClick = {
                    AxisHaptics.press(view)
                    vm.complete(onContinue)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
