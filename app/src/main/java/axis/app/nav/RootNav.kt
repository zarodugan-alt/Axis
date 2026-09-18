package axis.app.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import axis.app.onboarding.OnboardingPlaceholder
import axis.app.root.RootPager
import axis.app.settings.PlaceholderScreen
import axis.app.settings.SettingsRootScreen
import axis.app.settings.appearance.AppearanceScreen
import axis.app.settings.appearance.HiddenAppsScreen
import axis.app.splash.SplashScreen

/**
 * Root navigation (spec Part 4): splash → onboarding/root-pager, with the
 * settings/chat/routine stack pushed over the pager. Stack pushes slide on
 * X + fade (300ms); splash/root crossfade.
 */
@Composable
fun RootNav() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(250)) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onDone = { needs ->
                nav.navigate(if (needs) Routes.ONBOARDING else Routes.ROOT) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingPlaceholder(onContinue = {
                nav.navigate(Routes.ROOT) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.ROOT) {
            RootPager(onNavigate = { nav.navigate(it) })
        }

        stack(Routes.CHAT) {
            PlaceholderScreen(
                title = "Chat",
                phase = "Phase 3",
                description = "Streaming AI chat with the provider switcher and action " +
                    "chips lands with the LLM gateway in P3.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.ROUTINES) {
            PlaceholderScreen(
                title = "Routines",
                phase = "Phase 4",
                description = "The routine gallery (Good Night, Driving, Meeting, …) " +
                    "arrives with the routine engine in P4.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.ROUTINE_DETAIL) {
            PlaceholderScreen(
                title = "Routine",
                phase = "Phase 4",
                description = "Routine configuration arrives with the routine engine in P4.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS) {
            SettingsRootScreen(
                onNavigate = { nav.navigate(it) },
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_PROVIDERS) {
            PlaceholderScreen(
                title = "AI Providers",
                phase = "Phase 3",
                description = "BYOK setup for Groq, Mistral, Gemini and Unreal Speech — " +
                    "paste-key validation, model pickers and routing modes.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_APPEARANCE) {
            AppearanceScreen(
                onNavigate = { nav.navigate(it) },
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.HIDDEN_APPS) {
            HiddenAppsScreen(onBack = { nav.popBackStack() })
        }
        stack(Routes.SETTINGS_AUTOMATIONS) {
            PlaceholderScreen(
                title = "Automations",
                phase = "Phase 3",
                description = "Learned playbooks and memory arrive with the agent loop in P3.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_NOTIFICATIONS) {
            PlaceholderScreen(
                title = "Notification Rules",
                phase = "Phase 2",
                description = "Priority apps, contacts, keywords and quiet hours arrive " +
                    "with the notification listener in P2.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_VOICE) {
            PlaceholderScreen(
                title = "Voice",
                phase = "Phase 4",
                description = "On-device speech recognition, Unreal Speech voices and " +
                    "the voice HUD arrive with the voice pipeline in P4.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_SAFETY) {
            PlaceholderScreen(
                title = "Safety",
                phase = "Phase 3",
                description = "Protected apps, confirmation gates and the audit log " +
                    "arrive with the safety module in P3.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_USAGE) {
            PlaceholderScreen(
                title = "Usage Dashboard",
                phase = "Phase 4",
                description = "Per-provider requests, tokens, failovers and latency " +
                    "charts arrive in P4.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_ADVANCED) {
            PlaceholderScreen(
                title = "Advanced",
                phase = "Phase 4",
                description = "Flow Studio, the webhook server and settings export " +
                    "arrive in P4.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_ABOUT) {
            PlaceholderScreen(
                title = "About",
                phase = "Phase 2",
                description = "Version, open-source licenses and credits — the " +
                    "licenses screen is generated in P2.",
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.FLOW_STUDIO) {
            PlaceholderScreen(
                title = "Flow Studio",
                phase = "Phase 4",
                description = "The power-user flow editor arrives in P4.",
                onBack = { nav.popBackStack() }
            )
        }
    }
}

/** A stack destination with the standard slide-X + fade push transition. */
private fun androidx.navigation.NavGraphBuilder.stack(
    route: String,
    content: @Composable () -> Unit
) {
    composable(
        route = route,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) +
                fadeIn(tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) +
                fadeOut(tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) +
                fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) +
                fadeOut(tween(300))
        },
        content = { content() }
    )
}
