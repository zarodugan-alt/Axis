package axis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import axis.app.nav.RootNav
import axis.ui.theme.AxisTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity launcher host. Permanently thin: edge-to-edge + theme +
 * nav graph. Screens, overlays and back-stack behavior all live downstream.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxisTheme {
                RootNav()
            }
        }
    }
}
