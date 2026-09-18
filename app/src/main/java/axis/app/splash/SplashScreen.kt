package axis.app.splash

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import axis.app.data.SettingsStore
import axis.ui.components.AxisOrb
import axis.ui.components.OrbState
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settings: SettingsStore
) : ViewModel() {
    suspend fun needsOnboarding(): Boolean = !settings.onboardingDone.first()
}

/**
 * Splash (spec §S0): orb + wordmark + 700ms scanline sweep. Exits when init
 * is done AND ≥600ms elapsed (900ms hard cap — init is a DataStore read,
 * so the cap is structural, not a race).
 */
@Composable
fun SplashScreen(
    onDone: (needsOnboarding: Boolean) -> Unit,
    vm: SplashViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        val needs = vm.needsOnboarding()
        val elapsed = SystemClock.uptimeMillis() - start
        delay((600 - elapsed).coerceAtLeast(0))
        onDone(needs)
    }

    val scan = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scan.animateTo(1f, tween(700))
    }

    AxisBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            AxisOrb(OrbState.IDLE, size = 96.dp)
            Spacer(Modifier.height(24.dp))
            Text("AXIS", style = AxisType.Display.copy(letterSpacing = 8.sp))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (scan.value * constraints.maxHeight).roundToInt()) }
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AcccentCyan.copy(alpha = 0.8f))
            )
        }
    }
}
