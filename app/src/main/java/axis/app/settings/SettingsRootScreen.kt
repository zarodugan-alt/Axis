package axis.app.settings

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import axis.app.nav.Routes
import axis.ui.components.SettingsNavRow
import axis.ui.theme.AxisBackground
import axis.ui.theme.AxisHaptics
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisType
import axis.ui.theme.TextSecondary

/**
 * Settings root (spec §S10). Every group routes to a real screen —
 * providers, chat, automations, rules, voice, safety, usage, status, appearance.
 */
@Composable
fun SettingsRootScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    fun go(route: String) {
        AxisHaptics.press(view)
        onNavigate(route)
    }

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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = AxisType.Title)
            }
            Spacer(Modifier.height(8.dp))
            SettingsNavRow(
                title = "AI Providers",
                subtitle = "BYOK keys, models, routing",
                icon = Icons.Rounded.Memory, onClick = { go(Routes.SETTINGS_PROVIDERS) }
            )
            SettingsNavRow(
                title = "Chat with AXIS",
                subtitle = "Streaming agent, tools, confirmations",
                icon = Icons.Rounded.Chat, onClick = { go(Routes.CHAT) }
            )
            SettingsNavRow(
                title = "Automations",
                subtitle = "Armed routines, triggers, run history",
                icon = Icons.Rounded.Bolt, onClick = { go(Routes.ROUTINES) }
            )
            SettingsNavRow(
                title = "Notification Rules",
                subtitle = "Priority apps, keywords, quiet hours",
                icon = Icons.Rounded.Notifications, onClick = { go(Routes.SETTINGS_NOTIFICATIONS) }
            )
            SettingsNavRow(
                title = "Voice",
                subtitle = "Speech in, speech out, spoken replies",
                icon = Icons.Rounded.Mic, onClick = { go(Routes.SETTINGS_VOICE) }
            )
            SettingsNavRow(
                title = "Safety",
                subtitle = "Kill switch, gates, protected apps, audit",
                icon = Icons.Rounded.Shield, onClick = { go(Routes.SETTINGS_SAFETY) }
            )
            SettingsNavRow(
                title = "Usage Dashboard",
                subtitle = "Requests, tokens, latency, failures",
                icon = Icons.Rounded.BarChart, onClick = { go(Routes.SETTINGS_USAGE) }
            )
            SettingsNavRow(
                title = "System status",
                subtitle = "Permissions and services, live",
                icon = Icons.Rounded.Speed, onClick = { go(Routes.SYSTEM_STATUS) }
            )
            SettingsNavRow(
                title = "Appearance",
                subtitle = "Transition, haptics, icons",
                icon = Icons.Rounded.Palette, onClick = { go(Routes.SETTINGS_APPEARANCE) }
            )
            SettingsNavRow(
                title = "Advanced",
                subtitle = "Logs, export, Flow Studio, webhook",
                icon = Icons.Rounded.Build, onClick = { go(Routes.SETTINGS_ADVANCED) }
            )
            SettingsNavRow(
                title = "About",
                subtitle = "Version, licences, credits",
                icon = Icons.Rounded.Info, onClick = { go(Routes.SETTINGS_ABOUT) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
