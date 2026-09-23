package com.mymonstervr.kawabi.app

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadState
import com.mymonstervr.kawabi.app.update.AppUpdateNotifier
import com.mymonstervr.kawabi.app.update.AppUpdateStateHolder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import org.koin.compose.koinInject
import com.mymonstervr.kawabi.app.anime.AnimeBrowseScreen
import com.mymonstervr.kawabi.app.anime.AnimeDetailScreen
import com.mymonstervr.kawabi.app.anime.AnimeScreen
import com.mymonstervr.kawabi.app.anime.AnimeSearchScreen
import com.mymonstervr.kawabi.app.anime.PlayerScreen
import com.mymonstervr.kawabi.app.auth.LoginScreen
import com.mymonstervr.kawabi.app.browse.BrowseScreen
import com.mymonstervr.kawabi.app.detail.MangaDetailScreen
import com.mymonstervr.kawabi.app.library.LibraryScreen
import com.mymonstervr.kawabi.app.reader.ReaderScreen
import com.mymonstervr.kawabi.app.search.SearchScreen
import com.mymonstervr.kawabi.app.settings.AnimeSourcesScreen
import com.mymonstervr.kawabi.app.settings.BackupScreen
import com.mymonstervr.kawabi.app.settings.ChangelogScreen
import com.mymonstervr.kawabi.app.settings.SettingsScreen
import com.mymonstervr.kawabi.app.settings.SourcesScreen
import com.mymonstervr.kawabi.app.settings.TrackingServicesScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SOURCES = "sources"
private const val ROUTE_ANIME_SOURCES = "anime-sources"
private const val ROUTE_BACKUP = "backup"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_CHANGELOG = "changelog"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MANGA_DETAIL = "manga/{url}"
private const val ROUTE_READER = "reader/{chapterId}"
private const val ROUTE_BROWSE = "browse/{sourceKey}"
private const val ROUTE_ANIME_LIBRARY = "anime-library"
// Optional prefill query (tracker-list import's unmatched titles tap straight into a
// search for that title); navigating to the bare "anime-search" still matches.
private const val ROUTE_ANIME_SEARCH = "anime-search?q={q}"
private const val ROUTE_ANIME_DETAIL = "anime/{key}"
private const val ROUTE_ANIME_BROWSE = "anime-browse/{sourceKey}"
private const val ROUTE_PLAYER = "player/{episodeKey}"
private const val ARG_URL = "url"
private const val ARG_KEY = "key"
private const val ARG_CHAPTER_ID = "chapterId"
private const val ARG_EPISODE_KEY = "episodeKey"
private const val ARG_SOURCE_KEY = "sourceKey"
private const val ARG_QUERY = "q"

private data class BottomNavItem(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val bottomNavRoutes = listOf(
    BottomNavItem(ROUTE_LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    BottomNavItem(ROUTE_SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(ROUTE_ANIME_LIBRARY, "Anime", Icons.Filled.Movie, Icons.Outlined.Movie),
    BottomNavItem(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// Rapid double-taps (or spamming the back arrow) can fire navigate()/popBackStack()
// again before Compose Navigation finishes processing the first one -- the current
// destination's lifecycle briefly drops out of RESUMED while a transition is in
// flight, and a second call landing in that window corrupts the back stack (confirmed
// live: repeated fast back-presses left the app on a blank Library screen with
// correct underlying state -- the window itself, not the data, ended up broken).
// Standard fix (Android's own recommendation for exactly this): only act when the
// current entry is actually RESUMED, so a queued-up extra tap during a transition is
// silently dropped instead of firing a second navigation.
private fun androidx.navigation.NavController.isReadyToNavigate(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED

private fun androidx.navigation.NavController.navigateSafe(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    if (isReadyToNavigate()) navigate(route, builder)
}

private fun androidx.navigation.NavController.popBackStackSafe() {
    if (isReadyToNavigate()) popBackStack()
}

private fun navigateToMangaDetail(navController: androidx.navigation.NavController, url: String) {
    navController.navigateSafe("manga/${Uri.encode(url)}")
}

private fun navigateToBrowse(navController: androidx.navigation.NavController, sourceKey: String) {
    navController.navigateSafe("browse/${Uri.encode(sourceKey)}")
}

private fun navigateToAnimeDetail(navController: androidx.navigation.NavController, key: String) {
    navController.navigateSafe("anime/${Uri.encode(key)}")
}

private fun navigateToAnimeSearch(navController: androidx.navigation.NavController, query: String = "") {
    navController.navigateSafe("anime-search?q=${Uri.encode(query)}")
}

private fun navigateToAnimeBrowse(navController: androidx.navigation.NavController, sourceKey: String) {
    navController.navigateSafe("anime-browse/${Uri.encode(sourceKey)}")
}

private fun navigateToPlayer(navController: androidx.navigation.NavController, episodeKey: String) {
    navController.navigateSafe("player/${Uri.encode(episodeKey)}")
}

private fun navigateToEpisode(navController: androidx.navigation.NavController, episodeKey: String) {
    // Next/previous episode from inside the player replaces the current entry instead of
    // stacking another one, exactly like the reader's chapter hop -- Back returns to the
    // episode list, not through every episode watched this session.
    navController.navigateSafe("player/${Uri.encode(episodeKey)}") {
        popUpTo(ROUTE_PLAYER) { inclusive = true }
    }
}

private fun navigateToReader(navController: androidx.navigation.NavController, chapterId: Long) {
    navController.navigateSafe("reader/$chapterId")
}

private fun navigateToChapter(navController: androidx.navigation.NavController, chapterId: Long) {
    // Jumping chapter-to-chapter from inside the reader replaces the current entry
    // rather than pushing another one, so Back doesn't require walking through
    // every chapter you've hopped via '‹ ›'.
    navController.navigateSafe("reader/$chapterId") {
        popUpTo(ROUTE_READER) { inclusive = true }
    }
}

@Composable
fun KawabiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val context = LocalContext.current
    val updateStateHolder = koinInject<AppUpdateStateHolder>()
    val updateNotifier = koinInject<AppUpdateNotifier>()
    val downloadState by updateStateHolder.state.collectAsState()

    // Auto-launches the installer the moment the download finishes, as long as the app is
    // in the foreground to do it from -- Android blocks starting an activity from a purely
    // background context, so this only fires while some screen is actually on top; if the
    // app was fully closed the worker's own notification (tap to install) is the fallback.
    LaunchedEffect(downloadState) {
        val ready = downloadState as? AppUpdateDownloadState.ReadyToInstall ?: return@LaunchedEffect
        context.startActivity(updateNotifier.buildInstallIntent(File(ready.apkPath)))
    }

    Scaffold(
        bottomBar = {
            Column {
                val downloading = downloadState as? AppUpdateDownloadState.Downloading
                if (downloading != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            text = if (downloading.percent >= 0) "Downloading update -- ${downloading.percent}%" else "Downloading update...",
                            color = NightSession.TextDim,
                        )
                        LinearProgressIndicator(
                            progress = { if (downloading.percent >= 0) downloading.percent / 100f else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = NightSession.Chip,
                        )
                    }
                }
                if (currentRoute in bottomNavRoutes.map { it.route }) {
                    NavigationBar(containerColor = NightSession.Background) {
                        bottomNavRoutes.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigateSafe(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = NightSession.TextDim,
                                    unselectedTextColor = NightSession.TextDim,
                                    indicatorColor = NightSession.Chip,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    onMangaClick = { url -> navigateToMangaDetail(navController, url) },
                )
            }
            composable(ROUTE_SEARCH) {
                SearchScreen(
                    onResultClick = { url -> navigateToMangaDetail(navController, url) },
                    onBrowseClick = { sourceKey -> navigateToBrowse(navController, sourceKey) },
                )
            }
            composable(ROUTE_LOGIN) {
                LoginScreen(onDone = { navController.popBackStackSafe() })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onAccountClick = { navController.navigateSafe(ROUTE_LOGIN) },
                    onSourcesClick = { navController.navigateSafe(ROUTE_SOURCES) },
                    onAnimeSourcesClick = { navController.navigateSafe(ROUTE_ANIME_SOURCES) },
                    onBackupClick = { navController.navigateSafe(ROUTE_BACKUP) },
                    onTrackingClick = { navController.navigateSafe(ROUTE_TRACKING) },
                    onChangelogClick = { navController.navigateSafe(ROUTE_CHANGELOG) },
                )
            }
            composable(ROUTE_CHANGELOG) {
                ChangelogScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_SOURCES) {
                SourcesScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_ANIME_SOURCES) {
                AnimeSourcesScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_ANIME_LIBRARY) {
                AnimeScreen(
                    onAnimeClick = { key -> navigateToAnimeDetail(navController, key) },
                    onEpisodeClick = { episodeKey -> navigateToPlayer(navController, episodeKey) },
                    onSearchClick = { navigateToAnimeSearch(navController) },
                )
            }
            composable(
                route = ROUTE_ANIME_SEARCH,
                arguments = listOf(navArgument(ARG_QUERY) { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val initialQuery = Uri.decode(entry.arguments?.getString(ARG_QUERY).orEmpty())
                AnimeSearchScreen(
                    initialQuery = initialQuery,
                    onResultClick = { key -> navigateToAnimeDetail(navController, key) },
                    onBrowseClick = { sourceKey -> navigateToAnimeBrowse(navController, sourceKey) },
                )
            }
            composable(
                route = ROUTE_ANIME_DETAIL,
                arguments = listOf(navArgument(ARG_KEY) { type = NavType.StringType }),
            ) { entry ->
                val key = Uri.decode(entry.arguments?.getString(ARG_KEY).orEmpty())
                AnimeDetailScreen(
                    animeKey = key,
                    onBack = { navController.popBackStackSafe() },
                    onEpisodeClick = { episodeKey -> navigateToPlayer(navController, episodeKey) },
                    onOpenAnimeDetail = { libraryKey -> navigateToAnimeDetail(navController, libraryKey) },
                    onOpenTrackingSettings = { navController.navigateSafe(ROUTE_TRACKING) },
                    onKeyChanged = { newKey ->
                        navController.navigateSafe("anime/${Uri.encode(newKey)}") {
                            popUpTo(ROUTE_ANIME_DETAIL) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = ROUTE_ANIME_BROWSE,
                arguments = listOf(navArgument(ARG_SOURCE_KEY) { type = NavType.StringType }),
            ) { entry ->
                val sourceKey = Uri.decode(entry.arguments?.getString(ARG_SOURCE_KEY).orEmpty())
                AnimeBrowseScreen(
                    sourceKey = sourceKey,
                    onBack = { navController.popBackStackSafe() },
                    onResultClick = { key -> navigateToAnimeDetail(navController, key) },
                )
            }
            composable(
                route = ROUTE_PLAYER,
                arguments = listOf(navArgument(ARG_EPISODE_KEY) { type = NavType.StringType }),
            ) { entry ->
                val episodeKey = Uri.decode(entry.arguments?.getString(ARG_EPISODE_KEY).orEmpty())
                PlayerScreen(
                    episodeKey = episodeKey,
                    onBack = { navController.popBackStackSafe() },
                    onNavigateEpisode = { targetKey -> navigateToEpisode(navController, targetKey) },
                    onOpenAnimeDetail = { key -> navigateToAnimeDetail(navController, key) },
                )
            }
            composable(ROUTE_BACKUP) {
                BackupScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_TRACKING) {
                TrackingServicesScreen(
                    onBack = { navController.popBackStackSafe() },
                    onOpenAnimeSearch = { title -> navigateToAnimeSearch(navController, title) },
                )
            }
            composable(
                route = ROUTE_MANGA_DETAIL,
                arguments = listOf(navArgument(ARG_URL) { type = NavType.StringType }),
            ) { entry ->
                val url = Uri.decode(entry.arguments?.getString(ARG_URL).orEmpty())
                MangaDetailScreen(
                    url = url,
                    onBack = { navController.popBackStackSafe() },
                    onChapterClick = { chapterId -> navigateToReader(navController, chapterId) },
                    onOpenTrackingSettings = { navController.navigateSafe(ROUTE_TRACKING) },
                )
            }
            composable(
                route = ROUTE_BROWSE,
                arguments = listOf(navArgument(ARG_SOURCE_KEY) { type = NavType.StringType }),
            ) { entry ->
                val sourceKey = Uri.decode(entry.arguments?.getString(ARG_SOURCE_KEY).orEmpty())
                BrowseScreen(
                    sourceKey = sourceKey,
                    onBack = { navController.popBackStackSafe() },
                    onResultClick = { url -> navigateToMangaDetail(navController, url) },
                )
            }
            composable(
                route = ROUTE_READER,
                arguments = listOf(navArgument(ARG_CHAPTER_ID) { type = NavType.LongType }),
            ) { entry ->
                val chapterId = entry.arguments?.getLong(ARG_CHAPTER_ID) ?: 0L
                ReaderScreen(
                    chapterId = chapterId,
                    onBack = { navController.popBackStackSafe() },
                    onNavigateChapter = { targetId -> navigateToChapter(navController, targetId) },
                )
            }
        }
    }
}
