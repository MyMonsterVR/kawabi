package com.mymonstervr.kawabi.app

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mymonstervr.kawabi.app.theme.NightSession
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mymonstervr.kawabi.app.auth.LoginScreen
import com.mymonstervr.kawabi.app.detail.MangaDetailScreen
import com.mymonstervr.kawabi.app.library.LibraryScreen
import com.mymonstervr.kawabi.app.reader.ReaderScreen
import com.mymonstervr.kawabi.app.search.SearchScreen
import com.mymonstervr.kawabi.app.settings.BackupScreen
import com.mymonstervr.kawabi.app.settings.SettingsScreen
import com.mymonstervr.kawabi.app.settings.SourcesScreen
import com.mymonstervr.kawabi.app.settings.TrackingServicesScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SOURCES = "sources"
private const val ROUTE_BACKUP = "backup"
private const val ROUTE_TRACKING = "tracking"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MANGA_DETAIL = "manga/{url}"
private const val ROUTE_READER = "reader/{chapterId}"
private const val ARG_URL = "url"
private const val ARG_CHAPTER_ID = "chapterId"

private data class BottomNavItem(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val bottomNavRoutes = listOf(
    BottomNavItem(ROUTE_LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    BottomNavItem(ROUTE_SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
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

    Scaffold(
        bottomBar = {
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
                )
            }
            composable(ROUTE_LOGIN) {
                LoginScreen(onDone = { navController.popBackStackSafe() })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    onAccountClick = { navController.navigateSafe(ROUTE_LOGIN) },
                    onSourcesClick = { navController.navigateSafe(ROUTE_SOURCES) },
                    onBackupClick = { navController.navigateSafe(ROUTE_BACKUP) },
                    onTrackingClick = { navController.navigateSafe(ROUTE_TRACKING) },
                )
            }
            composable(ROUTE_SOURCES) {
                SourcesScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_BACKUP) {
                BackupScreen(onBack = { navController.popBackStackSafe() })
            }
            composable(ROUTE_TRACKING) {
                TrackingServicesScreen(onBack = { navController.popBackStackSafe() })
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
