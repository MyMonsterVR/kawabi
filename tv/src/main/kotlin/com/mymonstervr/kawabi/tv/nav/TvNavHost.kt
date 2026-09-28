package com.mymonstervr.kawabi.tv.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.MergeDuplicateAnimes
import com.mymonstervr.kawabi.domain.repository.CategoryRepository
import com.mymonstervr.kawabi.tv.detail.TvDetailScreen
import com.mymonstervr.kawabi.tv.home.TvHomeScreen
import com.mymonstervr.kawabi.tv.pairing.PairingScreen
import com.mymonstervr.kawabi.tv.player.TvPlayerScreen
import com.mymonstervr.kawabi.tv.search.TvSearchScreen
import com.mymonstervr.kawabi.tv.settings.TvSettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ROUTE_PAIRING = "pairing"
private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_DETAIL = "detail/{key}"
private const val ROUTE_PLAYER = "player/{episodeKey}"
private const val ARG_KEY = "key"
private const val ARG_EPISODE_KEY = "episodeKey"

private fun NavController.navigateToDetail(key: String) = navigate("detail/${Uri.encode(key)}")
private fun NavController.navigateToPlayer(episodeKey: String) = navigate("player/${Uri.encode(episodeKey)}")

/**
 * QR-only sign-in gate: no email/password fallback screen on the TV (matches the plan's
 * decision). [TokenStore.isLoggedIn] is watched continuously, not just read once at start,
 * so a 401-triggered token-clear from anywhere in the app bounces straight back to Pairing
 * with the back stack cleared -- never leaves a signed-out session stuck on a screen that
 * needs auth.
 */
@Composable
fun TvNavHost() {
    val navController = rememberNavController()
    val tokenStore = koinInject<TokenStore>()
    val isLoggedIn by tokenStore.isLoggedIn.collectAsState()

    val scope = koinInject<CoroutineScope>()
    val dispatchers = koinInject<AppDispatchers>()
    val mergeDuplicateAnimes = koinInject<MergeDuplicateAnimes>()
    val categoryRepository = koinInject<CategoryRepository>()
    val animeSyncClient = koinInject<AnimeSyncClient>()
    val trackerManager = koinInject<TrackerManager>()
    var didBootstrap by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        val target = if (isLoggedIn) ROUTE_HOME else ROUTE_PAIRING
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
        // Same bootstrap the phone app runs on launch (KawabiApplication.onCreate), minus the
        // manga-side SyncClient -- this app is anime-only. Pulls the backend's /anime/entries
        // snapshot into this device's own local DB so Home's continue-watching row and the
        // player's resume/progress tracking (both keyed off local rows, see PlayerViewModel's
        // resolveLocalEpisode) actually have something to read. Gated on login (not app
        // startup) since there's nothing to sync before pairing, and guarded so a token
        // refresh mid-session doesn't re-run it.
        if (isLoggedIn && !didBootstrap) {
            didBootstrap = true
            scope.launch(dispatchers.io) { mergeDuplicateAnimes.merge() }
            scope.launch {
                categoryRepository.ensureDefault()
                animeSyncClient.sync()
                trackerManager.refreshIfVerifyDue()
            }
        }
    }

    NavHost(navController = navController, startDestination = if (isLoggedIn) ROUTE_HOME else ROUTE_PAIRING) {
        composable(ROUTE_PAIRING) { PairingScreen() }
        composable(ROUTE_HOME) {
            TvHomeScreen(
                onSignOut = { tokenStore.clearToken() },
                onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenAnime = { key -> navController.navigateToDetail(key) },
                onOpenEpisode = { episodeKey -> navController.navigateToPlayer(episodeKey) },
            )
        }
        composable(ROUTE_SEARCH) {
            TvSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenAnime = { key -> navController.navigateToDetail(key) },
            )
        }
        composable(ROUTE_SETTINGS) {
            TvSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_KEY) { type = NavType.StringType }),
        ) { entry ->
            val key = Uri.decode(entry.arguments?.getString(ARG_KEY).orEmpty())
            TvDetailScreen(
                animeKey = key,
                onBack = { navController.popBackStack() },
                onOpenEpisode = { episodeKey -> navController.navigateToPlayer(episodeKey) },
            )
        }
        composable(
            route = ROUTE_PLAYER,
            arguments = listOf(navArgument(ARG_EPISODE_KEY) { type = NavType.StringType }),
        ) { entry ->
            val episodeKey = Uri.decode(entry.arguments?.getString(ARG_EPISODE_KEY).orEmpty())
            TvPlayerScreen(
                episodeKey = episodeKey,
                onBack = { navController.popBackStack() },
                onEpisodeChanged = { newKey ->
                    navController.navigate("player/${Uri.encode(newKey)}") {
                        popUpTo(ROUTE_PLAYER) { inclusive = true }
                    }
                },
            )
        }
    }
}
