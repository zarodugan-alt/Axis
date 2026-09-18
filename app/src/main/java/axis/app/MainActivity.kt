package axis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity launcher host. P1 scaffold content — replaced by the
 * RootNav graph (splash → onboarding/root-pager + settings stack) as the
 * P1 UI lands. The activity itself stays this thin permanently.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScaffoldContent() }
    }
}

@Composable
private fun ScaffoldContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B12)),
        contentAlignment = Alignment.Center
    ) {
        Text("AXIS", color = Color(0xFF00E5FF))
    }
}

@Preview
@Composable
private fun ScaffoldPreview() {
    ScaffoldContent()
}
