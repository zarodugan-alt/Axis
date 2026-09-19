package axis.app.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import axis.app.chat.ChatScreen
import axis.app.flow.FlowStudioScreen
import axis.app.notifications.NotificationCenterScreen
import axis.app.notifications.NotificationRulesScreen
import axis.app.onboarding.OnboardingScreen
import axis.app.root.RootPager
import axis.app.routines.RoutinesScreen
import axis.app.safety.SafetyScreen
import axis.app.settings.AboutScreen
import axis.app.settings.AdvancedScreen
import axis.app.settings.SettingsRootScreen
import axis.app.settings.appearance.AppearanceScreen
import axis.app.settings.appearance.HiddenAppsScreen
import axis.app.settings.providers.ProviderDetailScreen
import axis.app.settings.providers.ProvidersScreen
import axis.app.sidecar.SystemStatusScreen
import axis.app.splash.SplashScreen
import axis.app.usage.UsageScreen
import axis.app.voice.VoiceScreen

/**
 * Root navigation (spec Part 4): splash → onboarding/root-pager, with the
 * settings/chat/routine/agent stack pushed over the pager. Stack pushes slide
 * on X + fade (300ms); splash/root crossfade.
 *
 * Every destination is a real screen — there are no placeholders left.
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
            OnboardingScreen(
                onContinue = {
                    nav.navigate(Routes.ROOT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onOpenProviders = { nav.navigate(Routes.SETTINGS_PROVIDERS) }
            )
        }
        composable(Routes.ROOT) {
            RootPager(onNavigate = { nav.navigate(it) })
        }

        // ------------------------------------------------------------ agent
        stack(Routes.CHAT) {
            ChatScreen(
                onBack = { nav.popBackStack() },
                onOpenProviders = { nav.navigate(Routes.SETTINGS_PROVIDERS) }
            )
        }
        stack(Routes.NOTIFICATIONS) {
            NotificationCenterScreen(
                onBack = { nav.popBackStack() },
                onRules = { nav.navigate(Routes.SETTINGS_NOTIFICATIONS) }
            )
        }
        stack(Routes.SYSTEM_STATUS) {
            SystemStatusScreen(
                onBack = { nav.popBackStack() },
                onNavigate = { nav.navigate(it) }
            )
        }
        stack(Routes.SETTINGS_NOTIFICATIONS) {
            NotificationRulesScreen(onBack = { nav.popBackStack() })
        }
        stack(Routes.SETTINGS_VOICE) {
            VoiceScreen(
                onBack = { nav.popBackStack() },
                onOpenProviders = { nav.navigate(Routes.SETTINGS_PROVIDERS) }
            )
        }
        stack(Routes.SETTINGS_SAFETY) {
            SafetyScreen(onBack = { nav.popBackStack() })
        }
        stack(Routes.SETTINGS_USAGE) {
            UsageScreen(onBack = { nav.popBackStack() })
        }

        // --------------------------------------------------------- routines
        stack(Routes.ROUTINES) {
            RoutinesScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.routineDetail(id)) }
            )
        }
        stack(Routes.SETTINGS_AUTOMATIONS) {
            RoutinesScreen(
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.routineDetail(id)) }
            )
        }
        composable(
            route = Routes.ROUTINE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            FlowStudioScreen(
                routineId = entry.arguments?.getString("id"),
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.FLOW_STUDIO) {
            FlowStudioScreen(
                routineId = null,
                onBack = { nav.popBackStack() }
            )
        }

        // --------------------------------------------------------- settings
        stack(Routes.SETTINGS) {
            SettingsRootScreen(
                onNavigate = { nav.navigate(it) },
                onBack = { nav.popBackStack() }
            )
        }
        stack(Routes.SETTINGS_PROVIDERS) {
            ProvidersScreen(
                onNavigate = { nav.navigate(it) },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = Routes.SETTINGS_PROVIDER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            ProviderDetailScreen(
                providerId = entry.arguments?.getString("id"),
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
        stack(Routes.SETTINGS_ADVANCED) {
            AdvancedScreen(
                onBack = { nav.popBackStack() },
                onFlowStudio = { nav.navigate(Routes.FLOW_STUDIO) }
            )
        }
        stack(Routes.SETTINGS_ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() })
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
