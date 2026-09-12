package com.mymonstervr.kawabi.data.usecase

import android.util.Log
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.domain.model.TrackStatus
import kotlinx.coroutines.flow.first

private const val AUTO_IMPORT_INTERVAL_MS = 12 * 60 * 60 * 1000L
private const val TAG = "AutoImportAnime"

/**
 * Keeps the anime library in sync with each connected tracker's "Watching" list on its own,
 * the same way [SyncClient]/[AnimeSyncClient] keep progress in sync without the user asking --
 * see PLAN-anime.md section 14 for the import contract this drives. Called from [trackerIds]
 * rather than pulling [com.mymonstervr.kawabi.data.track.TrackerManager] itself, so this class
 * has no dependency on it (TrackerManager would otherwise need this class back for the
 * connect-trigger, which is a circular constructor dependency Koin can't resolve).
 */
class AutoImportAnimeFromTrackers(
    private val importAnimeFromTracker: ImportAnimeFromTracker,
    private val appPreferences: AppPreferences,
    private val tokenStore: TokenStore,
) {
    suspend fun run(trackerIds: Collection<String>, force: Boolean = false) {
        if (!tokenStore.isLoggedIn.value) return
        if (!appPreferences.animeAutoImportEnabled.first()) return

        val now = System.currentTimeMillis()
        for (trackerId in trackerIds) {
            val lastRun = appPreferences.animeLastAutoImportAt(trackerId)
            if (!force && now - lastRun < AUTO_IMPORT_INTERVAL_MS) continue

            runCatching {
                importAnimeFromTracker.import(trackerId, listOf(TrackStatus.WATCHING)).getOrThrow()
            }.onFailure { e ->
                Log.w(TAG, "Auto-import failed for tracker $trackerId", e)
            }
            appPreferences.setAnimeLastAutoImportAt(trackerId, now)
        }
    }
}
