package com.mymonstervr.kawabi.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.BuildConfig
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadState
import com.mymonstervr.kawabi.app.update.AppUpdateStateHolder
import com.mymonstervr.kawabi.data.update.AppUpdateChecker
import com.mymonstervr.kawabi.data.update.AppUpdateInfo
import com.mymonstervr.kawabi.app.notification.NewChapterNotifier
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.TrackerStatusState
import com.mymonstervr.kawabi.data.usecase.LibraryUpdateManager
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_DEFAULT
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_DEFAULT
import com.mymonstervr.kawabi.data.settings.PageFitMode
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_DEFAULT
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import com.mymonstervr.kawabi.data.settings.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val info: AppUpdateInfo) : UpdateCheckState
}

data class TrackerSummary(val name: String, val connected: Boolean, val expired: Boolean)

class SettingsViewModel(
    private val preferences: AppPreferences,
    tokenStore: TokenStore,
    private val updateChecker: AppUpdateChecker,
    private val trackerManager: TrackerManager,
    updateStateHolder: AppUpdateStateHolder,
    private val libraryUpdateManager: LibraryUpdateManager,
    private val newChapterNotifier: NewChapterNotifier,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = tokenStore.isLoggedIn

    val downloadState: StateFlow<AppUpdateDownloadState> = updateStateHolder.state

    val expiredTrackerCount: StateFlow<Int> = trackerManager.statuses
        .map { statuses -> statuses.values.count { it.expired } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trackerSummaries: StateFlow<List<TrackerSummary>> = trackerManager.statuses
        .map { statuses -> summariesFor(statuses) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summariesFor(trackerManager.statuses.value))

    private fun summariesFor(statuses: Map<String, TrackerStatusState>): List<TrackerSummary> =
        trackerManager.trackers.map { tracker ->
            val status = statuses[tracker.id]
            TrackerSummary(tracker.name, status?.connected == true, status?.expired == true)
        }

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            val result = updateChecker.check(forceCheck = true)
            _updateCheckState.value = result?.let(UpdateCheckState::Available) ?: UpdateCheckState.UpToDate
        }
    }

    val readingDirection: StateFlow<ReadingDirection> = preferences.readingDirection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingDirection.VERTICAL)

    val markReadOnScroll: StateFlow<Boolean> = preferences.markReadOnScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val keepScreenAwake: StateFlow<Boolean> = preferences.keepScreenAwake
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accentIndex: StateFlow<Int> = preferences.accentIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAccentIndex(index: Int) {
        viewModelScope.launch { preferences.setAccentIndex(index) }
    }

    fun setReadingDirection(direction: ReadingDirection) {
        viewModelScope.launch { preferences.setReadingDirection(direction) }
    }

    fun setMarkReadOnScroll(enabled: Boolean) {
        viewModelScope.launch { preferences.setMarkReadOnScroll(enabled) }
    }

    val newChapterNotificationsEnabled: StateFlow<Boolean> = preferences.newChapterNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setNewChapterNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNewChapterNotificationsEnabled(enabled) }
    }

    private val _forceLibraryUpdateStatus = MutableStateFlow("Checks every favorite now, ignoring the 6h schedule")
    val forceLibraryUpdateStatus: StateFlow<String> = _forceLibraryUpdateStatus.asStateFlow()

    // Debug/support action -- WorkManager's periodic scheduling makes the real 6h job
    // impossible to trigger on demand (even `adb shell cmd jobscheduler run -f` just gets
    // rescheduled, "before schedule"), so this calls the exact same notifier the worker
    // calls but against every favorite (checkAllFavoritesNow), bypassing the due-schedule
    // gate so a manual check can actually surface a real notification.
    fun forceLibraryUpdate() {
        viewModelScope.launch {
            _forceLibraryUpdateStatus.value = "Checking..."
            val result = libraryUpdateManager.checkAllFavoritesNow()
            if (result.updated.isNotEmpty() && preferences.newChapterNotificationsEnabled.first()) {
                newChapterNotifier.notify(result.updated)
            }
            _forceLibraryUpdateStatus.value = "Checked ${result.checked}, ${result.updated.size} had new chapters"
        }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch { preferences.setKeepScreenAwake(enabled) }
    }

    val libraryGridColumns: StateFlow<Int> = preferences.libraryGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LIBRARY_GRID_COLUMNS_DEFAULT)

    fun setLibraryGridColumns(count: Int) {
        viewModelScope.launch { preferences.setLibraryGridColumns(count) }
    }

    val pageFitMode: StateFlow<PageFitMode> = preferences.pageFitMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageFitMode.FIT_WIDTH)

    fun setPageFitMode(mode: PageFitMode) {
        viewModelScope.launch { preferences.setPageFitMode(mode) }
    }

    val markReadThreshold: StateFlow<Int> = preferences.markReadThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MARK_READ_THRESHOLD_DEFAULT)

    fun setMarkReadThreshold(percent: Int) {
        viewModelScope.launch { preferences.setMarkReadThreshold(percent) }
    }

    val themePalette: StateFlow<ThemePalette> = preferences.themePalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePalette.NIGHT_SESSION)

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { preferences.setThemePalette(palette) }
    }

    val amoledBlack: StateFlow<Boolean> = preferences.amoledBlack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { preferences.setAmoledBlack(enabled) }
    }

    val animeAutoMarkWatchedThreshold: StateFlow<Float> = preferences.animeAutoMarkWatchedThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT)

    fun setAnimeAutoMarkWatchedThreshold(fraction: Float) {
        viewModelScope.launch { preferences.setAnimeAutoMarkWatchedThreshold(fraction) }
    }

    val animePreferredQuality: StateFlow<String> = preferences.animePreferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setAnimePreferredQuality(quality: String) {
        viewModelScope.launch { preferences.setAnimePreferredQuality(quality) }
    }

    val animePreferredAudio: StateFlow<String> = preferences.animePreferredAudio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setAnimePreferredAudio(audio: String) {
        viewModelScope.launch { preferences.setAnimePreferredAudio(audio) }
    }

    val animeAutoSkipIntro: StateFlow<Boolean> = preferences.animeAutoSkipIntro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAnimeAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { preferences.setAnimeAutoSkipIntro(enabled) }
    }

    val subtitleTextSize: StateFlow<Int> = preferences.subtitleTextSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SUBTITLE_TEXT_SIZE_DEFAULT)

    fun setSubtitleTextSize(percent: Int) {
        viewModelScope.launch { preferences.setSubtitleTextSize(percent) }
    }

    val subtitleBackgroundStyle: StateFlow<SubtitleBackgroundStyle> = preferences.subtitleBackgroundStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleBackgroundStyle.OUTLINE)

    fun setSubtitleBackgroundStyle(style: SubtitleBackgroundStyle) {
        viewModelScope.launch { preferences.setSubtitleBackgroundStyle(style) }
    }

    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }
}
