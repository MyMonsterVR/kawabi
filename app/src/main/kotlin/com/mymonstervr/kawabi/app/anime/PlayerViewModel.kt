package com.mymonstervr.kawabi.app.anime

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.PlayerHttpClient
import com.mymonstervr.kawabi.data.network.dto.VideoDto
import com.mymonstervr.kawabi.data.network.dto.VideoTimestampDto
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.usecase.AnimeSyncClient
import com.mymonstervr.kawabi.data.usecase.AnimeTrackerSyncClient
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "PlayerViewModel"
private const val UNKNOWN_EPISODE_NUMBER = -1.0

// Position is sampled this often (skip-button visibility and the seek bar want a fresh
// number), but only persisted every PERSIST_EVERY_TICKS of those samples: every episode
// row update fires the version/last_modified triggers and bumps the parent anime row too,
// so a literal 1/s write would be ~1400 trigger cascades per episode for a resume position
// that nothing reads until the episode is reopened. The in-memory value is always current,
// and onCleared() flushes it -- same arrangement as the reader's trackPosition().
private const val TICK_MS = 500L
private const val PERSIST_EVERY_TICKS = 10

// Below this a stored position is treated as "didn't really start" -- resuming 2s in is
// more annoying than just starting over.
private const val MIN_RESUME_MS = 5_000L

/** One selectable stream: an extension hoster's name plus one of its quality variants. */
data class PlayerVideo(
    val hosterIndex: Int,
    val hosterName: String,
    val video: VideoDto,
) {
    // Sources without a real hoster list (the extensions-lib NO_HOSTER_LIST placeholder,
    // which reaches us verbatim as "no_hoster_list") would otherwise show that literal
    // token as the server name -- the quality title is the only meaningful label there.
    val displayHoster: String
        get() = hosterName.takeUnless { it.isBlank() || it.equals("no_hoster_list", ignoreCase = true) } ?: "Stream"
}

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data object Ready : PlayerUiState
    /** [canPickServer] is false only when there was never more than one stream to offer. */
    data class Error(val message: String, val canPickServer: Boolean) : PlayerUiState
}

/**
 * Sources whose direct-CDN URLs have been observed to fail from this device (e.g. WARP-IP-
 * bound streams that 403 outside the engine) -- once a source lands here, every later
 * episode from it is played through `proxy_url` from the start instead of retrying `url`
 * first. Process-wide and never persisted: a fresh process is worth one more direct attempt.
 */
private val preferProxyBySource = mutableSetOf<String>()

internal fun sourceIdFor(episodeKey: String): String = episodeKey.substringBefore(':')

@OptIn(UnstableApi::class)
class PlayerViewModel(
    context: Context,
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val preferences: AppPreferences,
    private val animeSyncClient: AnimeSyncClient,
    private val animeTrackerSyncClient: AnimeTrackerSyncClient,
    private val playerHttpClient: PlayerHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _animeTitle = MutableStateFlow("")
    val animeTitle: StateFlow<String> = _animeTitle.asStateFlow()

    // Null until resolveLocalEpisode() finishes -- lets the title bar's long-press-for-
    // details gesture no-op instead of navigating with a blank key while loading.
    private val _animeKey = MutableStateFlow<String?>(null)
    val animeKey: StateFlow<String?> = _animeKey.asStateFlow()

    private val _episodeTitle = MutableStateFlow("")
    val episodeTitle: StateFlow<String> = _episodeTitle.asStateFlow()

    private val _videos = MutableStateFlow<List<PlayerVideo>>(emptyList())
    val videos: StateFlow<List<PlayerVideo>> = _videos.asStateFlow()

    private val _currentVideo = MutableStateFlow<PlayerVideo?>(null)
    val currentVideo: StateFlow<PlayerVideo?> = _currentVideo.asStateFlow()

    /** Index into the current video's `subtitles`, or null for "off". */
    private val _selectedSubtitle = MutableStateFlow<Int?>(null)
    val selectedSubtitle: StateFlow<Int?> = _selectedSubtitle.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _ended = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _nextEpisodeKey = MutableStateFlow<String?>(null)
    val nextEpisodeKey: StateFlow<String?> = _nextEpisodeKey.asStateFlow()

    private val _previousEpisodeKey = MutableStateFlow<String?>(null)
    val previousEpisodeKey: StateFlow<String?> = _previousEpisodeKey.asStateFlow()

    private val _autoSkip = MutableStateFlow(false)
    val autoSkip: StateFlow<Boolean> = _autoSkip.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(OkHttpDataSource.Factory(playerHttpClient.client)),
        )
        .build()

    private var loadedKey: String? = null
    private var episode: Episode? = null
    private var animeId: Long? = null
    private var markWatchedThreshold: Float = ANIME_AUTO_MARK_WATCHED_THRESHOLD_DEFAULT
    private var alreadyMarkedWatched = false
    private var ticker: Job? = null
    private var ticks = 0
    private var autoSelectedSubtitle = false
    private var usingProxy = false
    private var reachedReadyForCurrent = false
    // Skipping is one-shot per range: after a manual seek back into the opening the user
    // clearly wants to watch it, so auto-skip must not yank them forward again.
    private val skippedRanges = mutableSetOf<Int>()

    // Mirrors ReaderViewModel's lastKnownProgress: leaving the player pops the nav entry
    // and cancels viewModelScope, so a write merely *launched* on it can silently never
    // run. onCleared() flushes this synchronously instead.
    private var pendingPositionMs = 0L
    private var pendingDurationMs = 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                reachedReadyForCurrent = true
                _state.value = PlayerUiState.Ready
                _ended.value = false
            }
            if (playbackState == Player.STATE_ENDED) {
                _ended.value = true
                onReachedEnd()
            }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            if (autoSelectedSubtitle) return
            val subtitles = _currentVideo.value?.video?.subtitles ?: return
            val english = subtitles.indexOfFirst { it.lang.contains("en", ignoreCase = true) }
            if (english >= 0) {
                autoSelectedSubtitle = true
                selectSubtitle(english)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "playback failed: ${error.errorCodeName}", error)
            val selection = _currentVideo.value
            val proxyUrl = selection?.video?.proxy_url
            val qualifiesForProxyFallback = !usingProxy && proxyUrl != null && (
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    (isIoErrorCode(error.errorCode) && !reachedReadyForCurrent)
                )
            if (qualifiesForProxyFallback && selection != null) {
                val sourceId = loadedKey?.let { sourceIdFor(it) }
                if (sourceId != null) preferProxyBySource += sourceId
                Log.i(
                    TAG,
                    "proxy fallback: source=$sourceId hoster=${selection.hosterName} " +
                        "error=${error.errorCodeName}",
                )
                play(selection, startAtMs = player.currentPosition.coerceAtLeast(0L))
                return
            }
            _state.value = PlayerUiState.Error(
                message = playerErrorMessage(error),
                canPickServer = _videos.value.size > 1,
            )
        }
    }

    init {
        player.addListener(listener)
        startTicker()
    }

    fun load(episodeKey: String) {
        if (loadedKey == episodeKey) return
        loadedKey = episodeKey
        viewModelScope.launch {
            _state.value = PlayerUiState.Loading
            markWatchedThreshold = preferences.animeAutoMarkWatchedThreshold.first()
            _autoSkip.value = preferences.animeAutoSkipIntro.first()
            val preferredQuality = preferences.animePreferredQuality.first()

            resolveLocalEpisode(episodeKey)

            animeApi.getVideos(episodeKey)
                .onSuccess { hosters ->
                    val flattened = hosters.flatMap { hoster ->
                        hoster.videos.map { PlayerVideo(hoster.hoster.index, hoster.hoster.name, it) }
                    }
                    _videos.value = flattened
                    val initial = pickInitialVideo(flattened, preferredQuality)
                    if (initial == null) {
                        _state.value = PlayerUiState.Error("No playable streams for this episode", canPickServer = false)
                        return@onSuccess
                    }
                    play(initial, startAtMs = resumePositionMs())
                }
                .onFailure {
                    _state.value = PlayerUiState.Error(it.message ?: "Couldn't load this episode's streams", canPickServer = false)
                }
        }
    }

    fun selectVideo(selection: PlayerVideo) {
        // Switching server/quality mid-episode keeps the place you were at -- the streams
        // are the same episode from different hosts, so the position carries over.
        play(selection, startAtMs = player.currentPosition.coerceAtLeast(0L))
    }

    // Anime HLS playlists routinely declare embedded CEA-608/708 closed-caption channels
    // per variant (usually unused boilerplate) alongside our externally-attached VTT/SRT/SSA
    // subtitle configurations. Both show up as TRACK_TYPE_TEXT groups, and the embedded ones
    // sort first -- indexing textGroups positionally against our own subtitles list picked
    // an empty CEA-608 placeholder instead of the real external track, so the subtitle
    // "selected" successfully (no crash, index in range) but nothing ever rendered.
    private val embeddedCaptionMimeTypes = setOf(MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708)

    private fun externalTextGroups() = player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .filterNot { group -> (0 until group.length).all { i -> group.getTrackFormat(i).sampleMimeType in embeddedCaptionMimeTypes } }

    fun selectSubtitle(index: Int?) {
        val textGroups = externalTextGroups()
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (index == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = textGroups.getOrNull(index) ?: return
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = params.build()
        _selectedSubtitle.value = index
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        player.setPlaybackSpeed(speed)
    }

    /** The Opening/Ending range the playhead is currently inside, if any. */
    fun activeSkipRange(positionMs: Long): VideoTimestampDto? =
        _currentVideo.value?.video?.timestamps?.firstOrNull { it.isSkippable() && positionMs in it.start until it.end }

    fun skip(range: VideoTimestampDto) {
        skippedRanges += range.start.toInt()
        player.seekTo(range.end)
    }

    fun pause() {
        player.pause()
        persistProgress()
    }

    private suspend fun resolveLocalEpisode(episodeKey: String) {
        // An episode opened from a non-favorited anime has no local row at all -- playback
        // still works, only progress/watched writes are skipped (there's nowhere to put
        // them), so every use of `episode` below is null-guarded rather than required.
        val local = episodeRepository.getByKey(episodeKey) ?: return
        episode = local
        alreadyMarkedWatched = local.watched
        animeId = local.animeId
        val anime = animeRepository.getById(local.animeId)
        _animeTitle.value = anime?.title.orEmpty()
        _animeKey.value = anime?.key
        _episodeTitle.value = local.name.ifBlank { "Episode ${formatChapterNumber(local.episodeNumber)}" }
        resolveNeighbours(local)
    }

    private suspend fun resolveNeighbours(current: Episode) {
        if (current.episodeNumber == UNKNOWN_EPISODE_NUMBER) return
        val siblings = episodeRepository.getForAnime(current.animeId)
            .filter { it.episodeNumber != UNKNOWN_EPISODE_NUMBER }
        _nextEpisodeKey.value = siblings.filter { it.episodeNumber > current.episodeNumber }
            .minByOrNull { it.episodeNumber }?.key
        _previousEpisodeKey.value = siblings.filter { it.episodeNumber < current.episodeNumber }
            .maxByOrNull { it.episodeNumber }?.key
    }

    private fun resumePositionMs(): Long {
        val local = episode ?: return 0L
        return if (!local.watched && local.positionMs > MIN_RESUME_MS) local.positionMs else 0L
    }

    private fun play(selection: PlayerVideo, startAtMs: Long) {
        val sourceId = loadedKey?.let { sourceIdFor(it) }
        usingProxy = selection.video.proxy_url != null && sourceId != null && sourceId in preferProxyBySource
        reachedReadyForCurrent = false
        Log.i(
            TAG,
            "playing hoster=${selection.hosterName} quality=${selection.video.title} " +
                "proxied=${selection.video.proxied} viaProxy=$usingProxy",
        )
        _currentVideo.value = selection
        _selectedSubtitle.value = null
        autoSelectedSubtitle = false
        skippedRanges.clear()

        // Extension-supplied Origin/Referer are scoped to the video's own CDN host via an
        // interceptor rather than DefaultMediaSourceFactory's blanket
        // setDefaultRequestProperties -- that applied them to every request the data source
        // makes, including external subtitle tracks, which routinely live on a completely
        // different host (e.g. Anikoto's video CDN vs. its separate subtitle CDN). Forcing
        // one hoster's Referer onto an unrelated subtitle host got it rejected, making
        // subtitles appear simply missing. User-Agent still goes on everywhere -- unlike
        // Origin/Referer it isn't tied to a specific site's anti-hotlinking check, and a lot
        // of CDNs (subtitle hosts included) reject OkHttp's default UA outright.
        val videoHost = runCatching { java.net.URI(selection.video.url).host }.getOrNull()
        val siteScopedHeaders = selection.video.headers.filterKeys { !it.equals("user-agent", ignoreCase = true) }
        val globalHeaders = selection.video.headers.filterKeys { it.equals("user-agent", ignoreCase = true) }
        val scopedClient = if (selection.video.headers.isEmpty()) {
            playerHttpClient.client
        } else {
            playerHttpClient.client.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val builder = request.newBuilder()
                    globalHeaders.forEach { (k, v) -> builder.header(k, v) }
                    if (videoHost != null && request.url.host.equals(videoHost, ignoreCase = true)) {
                        siteScopedHeaders.forEach { (k, v) -> builder.header(k, v) }
                    }
                    chain.proceed(builder.build())
                }
                .build()
        }
        player.setMediaSource(
            DefaultMediaSourceFactory(OkHttpDataSource.Factory(scopedClient))
                .createMediaSource(mediaItemFor(selection)),
        )
        player.playWhenReady = true
        if (startAtMs > 0) player.seekTo(startAtMs)
        player.prepare()
        _state.value = PlayerUiState.Loading
    }

    private fun mediaItemFor(selection: PlayerVideo): MediaItem {
        val uri = (selection.video.proxy_url.takeIf { usingProxy }) ?: selection.video.url
        return MediaItem.Builder()
            .setUri(uri)
            // The proxied form is "<base>/anime/stream/<port>/m3u8?url=..." -- the path has
            // no .m3u8 extension to sniff, so without an explicit MIME type the media
            // source factory would pick the progressive extractor and fail on a playlist.
            .setMimeType(MimeTypes.APPLICATION_M3U8.takeIf { uri.contains("m3u8", ignoreCase = true) })
            .setSubtitleConfigurations(
                selection.video.subtitles.map { track ->
                    val subtitleUri = (track.proxy_url.takeIf { usingProxy }) ?: track.url
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUri))
                        .setMimeType(subtitleMimeType(subtitleUri))
                        .setLanguage(track.lang.ifBlank { null })
                        .setLabel(track.lang.ifBlank { "Subtitles" })
                        .build()
                },
            )
            .build()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    private fun onTick() {
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        _positionMs.value = position
        if (!player.isPlaying) return

        pendingPositionMs = position
        pendingDurationMs = duration

        if (_autoSkip.value) {
            activeSkipRange(position)?.let { range ->
                if (skippedRanges.add(range.start.toInt())) player.seekTo(range.end)
            }
        }

        if (duration > 0 && position.toFloat() / duration >= markWatchedThreshold) markWatched()

        ticks++
        if (ticks % PERSIST_EVERY_TICKS == 0) persistProgress()
    }

    private fun persistProgress() {
        val local = episode ?: return
        if (pendingPositionMs <= 0) return
        viewModelScope.launch {
            episodeRepository.setProgress(local.id, alreadyMarkedWatched || local.watched, pendingPositionMs, pendingDurationMs)
            animeId?.let { animeRepository.touchLastWatched(it, System.currentTimeMillis()) }
        }
    }

    // Monotonic: watched only ever goes 0 -> 1 here, and only once per episode. Unmarking
    // stays an explicit user action on the detail screen.
    private fun markWatched() {
        if (alreadyMarkedWatched) return
        val local = episode ?: return
        alreadyMarkedWatched = true
        viewModelScope.launch {
            episodeRepository.setProgress(local.id, true, pendingPositionMs, pendingDurationMs)
            animeId?.let { animeRepository.touchLastWatched(it, System.currentTimeMillis()) }
            // Push right when an episode completes rather than waiting for the next app
            // start: a long watching session would otherwise never reach the backend.
            // Same reasoning (and same "only on completion, never per tick") as the
            // reader's justMarkedRead branch.
            runCatching { animeSyncClient.sync() }
            animeId?.let { id -> runCatching { animeTrackerSyncClient.pushLocalEpisodesWatched(id) } }
        }
    }

    private fun onReachedEnd() {
        // Reaching the literal end counts as watched regardless of the threshold: a stream
        // whose reported duration is short (or missing) could otherwise finish without ever
        // crossing it.
        markWatched()
        persistProgress()
    }

    override fun onCleared() {
        super.onCleared()
        ticker?.cancel()
        val local = episode
        val position = pendingPositionMs
        player.removeListener(listener)
        player.release()
        if (local == null || position <= 0) return
        runBlocking {
            episodeRepository.setProgress(local.id, alreadyMarkedWatched || local.watched, position, pendingDurationMs)
            animeId?.let { animeRepository.touchLastWatched(it, System.currentTimeMillis()) }
        }
    }

}

/**
 * Initial stream choice, in preference order: the user's stored quality if any variant's
 * title mentions it, whatever the extension flagged `preferred`, then the highest
 * resolution -- falling back to parsing "1080p" out of the title, since several extensions
 * leave `resolution` at 0 and only label the variant.
 */
internal fun pickInitialVideo(videos: List<PlayerVideo>, preferredQuality: String): PlayerVideo? {
    if (videos.isEmpty()) return null
    if (preferredQuality.isNotBlank()) {
        videos.firstOrNull { it.video.title.contains(preferredQuality, ignoreCase = true) }?.let { return it }
    }
    videos.firstOrNull { it.video.preferred }?.let { return it }
    return videos.maxByOrNull { resolutionOf(it.video) } ?: videos.first()
}

internal fun resolutionOf(video: VideoDto): Int =
    video.resolution.takeIf { it > 0 }
        ?: Regex("""(\d{3,4})\s*[pP]""").find(video.title)?.groupValues?.get(1)?.toIntOrNull()
        ?: 0

internal fun subtitleMimeType(url: String): String = when {
    url.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
    url.endsWith(".ass", ignoreCase = true) || url.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.TEXT_VTT
}

internal fun isIoErrorCode(errorCode: Int): Boolean = errorCode in 2000..2999

internal fun VideoTimestampDto.isSkippable(): Boolean =
    end > start && (type.equals("Opening", ignoreCase = true) || type.equals("Ending", ignoreCase = true))

private fun playerErrorMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "The server rejected this stream (${error.errorCodeName}). Try another server."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> "Couldn't reach the stream. Check your connection or try another server."
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    -> "This stream didn't play back correctly. Try another server."
    else -> error.localizedMessage ?: "Playback failed. Try another server."
}
