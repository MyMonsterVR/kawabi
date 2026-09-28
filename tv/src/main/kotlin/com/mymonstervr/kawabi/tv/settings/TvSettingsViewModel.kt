package com.mymonstervr.kawabi.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymonstervr.kawabi.data.network.AuthApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_DEFAULT
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import com.mymonstervr.kawabi.data.update.AppUpdateChecker
import com.mymonstervr.kawabi.data.update.AppUpdateInfo
import com.mymonstervr.kawabi.tv.BuildConfig
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadState
import com.mymonstervr.kawabi.tv.update.TvUpdateStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TvUpdateCheckState {
    data object Idle : TvUpdateCheckState
    data object Checking : TvUpdateCheckState
    data object UpToDate : TvUpdateCheckState
    data class Available(val info: AppUpdateInfo) : TvUpdateCheckState
}

class TvSettingsViewModel(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val updateChecker: AppUpdateChecker,
    private val preferences: AppPreferences,
    updateStateHolder: TvUpdateStateHolder,
) : ViewModel() {

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    // "" / "sub" / "dub", same values and same underlying DataStore key PlayerViewModel
    // already reads (see pickInitialVideo) -- purely local per-device storage, though, so
    // this is genuinely independent of whatever the phone app has it set to, not a sync bug.
    val animePreferredAudio: StateFlow<String> = preferences.animePreferredAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setPreferredAudio(audio: String) {
        viewModelScope.launch { preferences.setAnimePreferredAudio(audio) }
    }

    // "" / "1080p" / "720p" / "480p" -- same matching-against-stream-title approach as audio.
    val animePreferredQuality: StateFlow<String> = preferences.animePreferredQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setPreferredQuality(quality: String) {
        viewModelScope.launch { preferences.setAnimePreferredQuality(quality) }
    }

    val animeAutoSkipIntro: StateFlow<Boolean> = preferences.animeAutoSkipIntro
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { preferences.setAnimeAutoSkipIntro(enabled) }
    }

    val animeAutoMarkWatchedThreshold: StateFlow<Float> = preferences.animeAutoMarkWatchedThreshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT)

    fun setAutoMarkWatchedThreshold(fraction: Float) {
        viewModelScope.launch { preferences.setAnimeAutoMarkWatchedThreshold(fraction) }
    }

    val subtitleTextSize: StateFlow<Int> = preferences.subtitleTextSize
        .stateIn(viewModelScope, SharingStarted.Eagerly, SUBTITLE_TEXT_SIZE_DEFAULT)

    fun setSubtitleTextSize(percent: Int) {
        viewModelScope.launch { preferences.setSubtitleTextSize(percent) }
    }

    val subtitleBackgroundStyle: StateFlow<SubtitleBackgroundStyle> = preferences.subtitleBackgroundStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleBackgroundStyle.OUTLINE)

    fun setSubtitleBackgroundStyle(style: SubtitleBackgroundStyle) {
        viewModelScope.launch { preferences.setSubtitleBackgroundStyle(style) }
    }

    val downloadState: StateFlow<TvUpdateDownloadState> = updateStateHolder.state

    private val _updateCheckState = MutableStateFlow<TvUpdateCheckState>(TvUpdateCheckState.Idle)
    val updateCheckState: StateFlow<TvUpdateCheckState> = _updateCheckState.asStateFlow()

    init {
        viewModelScope.launch {
            authApi.me().onSuccess { _email.value = it.email }
        }
        // Silent background check on every Settings visit -- TV has no equivalent "just
        // opened the app" hook worth wiring separately since Settings is already where an
        // available update surfaces.
        checkForUpdate()
    }

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            _updateCheckState.value = TvUpdateCheckState.Checking
            val result = updateChecker.check(forceCheck = force)
            _updateCheckState.value = result?.let(TvUpdateCheckState::Available) ?: TvUpdateCheckState.UpToDate
        }
    }

    /** Only kills this TV's own session -- the phone that paired it stays logged in. */
    fun signOut() {
        viewModelScope.launch {
            authApi.logout()
            tokenStore.clearToken()
        }
    }
}
