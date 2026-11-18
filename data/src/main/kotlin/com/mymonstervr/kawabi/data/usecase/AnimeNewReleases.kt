package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeBrowseResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.domain.model.normalizeAnimeTitle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "New releases" pool: the latest page of every enabled source, merged into one list
 * where a show listed by several sources is one card. Pages are only fetched as far as the
 * caller's [fill] target needs, since the backend's catalog routes are rate limited.
 * Mirrors kawabi-web's NewReleasesRail.
 */
class AnimeNewReleases(private val animeApi: AnimeApi) {

    /** [audio] is null until known; "" is a real answer (checked, no tag). */
    data class ReleaseCard(
        val card: AnimeCardDto,
        val audio: String?,
        val dubSince: Long,
        val norm: String,
        val fullNorm: String,
    )

    data class State(
        val cards: List<ReleaseCard> = emptyList(),
        val started: Boolean = false,
        val loading: Boolean = false,
        val hasMore: Boolean = true,
        val anySuccess: Boolean = false,
        val sourcesFailed: Boolean = false,
    )

    private class SourceProgress(val key: String) {
        var page = 1
        var fails = 0
        var done = false
    }

    private val mutex = Mutex()
    private val lock = Any()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var sources: List<SourceProgress>? = null
    private val cards = mutableListOf<ReleaseCard>()
    private val owners = HashMap<String, Int>()
    private val audioCache = HashMap<String, String>()
    private var resetToken = 0

    /**
     * Fetches rounds (one page from every unfinished source, in parallel) until at least
     * [target] cards are eligible for [filter] or no source has more. A card is eligible
     * when it passes the filter or its audio is still unknown. A different [token] than
     * the previous call discards the pool first.
     */
    suspend fun fill(target: Int, filter: String, libraryNorms: Set<String>, token: Int) = mutex.withLock {
        if (token != resetToken) {
            resetToken = token
            clear()
        }
        _state.update { it.copy(started = true, loading = true) }
        try {
            val list = sources ?: loadSources() ?: return@withLock
            while (true) {
                currentCoroutineContext().ensureActive()
                if (eligibleCount(filter) >= target) break
                val active = list.filter { !it.done }
                if (active.isEmpty()) break
                val anyFailed = withContext(NonCancellable) {
                    val results = coroutineScope {
                        active.map { progress -> async { progress to fetch(progress) } }.awaitAll()
                    }
                    var failed = false
                    for ((progress, response) in results) {
                        if (response == null) {
                            progress.fails++
                            failed = true
                            if (progress.fails >= MAX_FAILS) progress.done = true
                        } else {
                            progress.fails = 0
                            progress.page++
                            if (!response.has_next_page || progress.page > MAX_PAGE) progress.done = true
                            ingest(response.results, libraryNorms)
                        }
                    }
                    failed
                }
                if (anyFailed && list.any { !it.done }) delay(RETRY_DELAY_MS)
            }
        } finally {
            _state.update { it.copy(loading = false, hasMore = sources?.any { p -> !p.done } ?: false) }
        }
    }

    /** Looks up audio for cards that arrived without it, 4 at a time. No-op for the "all" filter. */
    suspend fun resolveAudio(filter: String) {
        if (filter.isEmpty()) return
        val pending = _state.value.cards.filter { it.audio == null }.map { it.card.key }
        if (pending.isEmpty()) return
        val permits = Semaphore(AUDIO_CONCURRENCY)
        coroutineScope {
            for (key in pending) launch { lookupAudio(key, permits) }
        }
    }

    private suspend fun lookupAudio(key: String, permits: Semaphore) {
        for (attempt in 1..AUDIO_ATTEMPTS) {
            val result = permits.withPermit { animeApi.getAudio(key) }
            result.onSuccess {
                setAudio(key, it)
                return
            }
            if (attempt < AUDIO_ATTEMPTS) delay(AUDIO_RETRY_DELAY_MS * attempt)
        }
        setAudio(key, "")
    }

    private fun setAudio(key: String, audio: String) = synchronized(lock) {
        audioCache[key] = audio
        var changed = false
        for (i in cards.indices) {
            if (cards[i].card.key == key && cards[i].audio == null) {
                cards[i] = cards[i].copy(audio = audio)
                changed = true
            }
        }
        if (changed) publish()
    }

    private fun clear() = synchronized(lock) {
        sources = null
        cards.clear()
        owners.clear()
        _state.value = State()
    }

    private suspend fun loadSources(): List<SourceProgress>? {
        val response = animeApi.getSources().getOrNull()
        if (response == null) {
            _state.update { it.copy(sourcesFailed = true) }
            return null
        }
        val list = response.sources
            .filter { it.enabled && it.supports_latest && !it.adult }
            .map { SourceProgress(it.key) }
        sources = list
        _state.update { it.copy(sourcesFailed = false) }
        return list
    }

    private suspend fun fetch(progress: SourceProgress): AnimeBrowseResponse? =
        withTimeoutOrNull(BROWSE_TIMEOUT_MS) {
            animeApi.browse(progress.key, "latest", progress.page, recent = true, limit = PAGE_LIMIT).getOrNull()
        }

    private fun eligibleCount(filter: String): Int = synchronized(lock) {
        cards.count { filter.isEmpty() || it.audio == null || it.audio == filter }
    }

    private fun ingest(incoming: List<AnimeCardDto>, libraryNorms: Set<String>) = synchronized(lock) {
        for (card in incoming) {
            val norm = normalizeAnimeTitle(card.title).ifBlank { card.key }
            val fullNorm = card.fullTitle?.takeIf { it.isNotBlank() }
                ?.let { normalizeAnimeTitle(it) }
                ?.ifBlank { null }
                ?: norm
            if (norm in libraryNorms || fullNorm in libraryNorms) continue
            val identities = listOfNotNull(
                norm,
                fullNorm,
                card.canonicalId?.takeIf { it > 0 }?.let { "id:$it" },
            ).distinct()
            val dubSince = card.dubSince ?: 0L
            val owner = identities.firstNotNullOfOrNull { owners[it] }
            if (owner != null) {
                val existing = cards[owner].dubSince
                if (dubSince > 0 && (existing <= 0 || dubSince < existing)) {
                    cards[owner] = cards[owner].copy(dubSince = dubSince)
                }
                continue
            }
            val audio = if (card.audioKnown) card.audio.orEmpty() else audioCache[card.key]
            val index = cards.size
            identities.forEach { owners[it] = index }
            cards += ReleaseCard(card, audio, dubSince, norm, fullNorm)
        }
        _state.update { it.copy(anySuccess = true) }
        publish()
    }

    private fun publish() {
        val snapshot = cards.toList()
        _state.update { it.copy(cards = snapshot) }
    }

    private companion object {
        const val MAX_FAILS = 3
        const val MAX_PAGE = 20
        const val PAGE_LIMIT = 24
        const val BROWSE_TIMEOUT_MS = 8_000L
        const val RETRY_DELAY_MS = 2_000L
        const val AUDIO_CONCURRENCY = 4
        const val AUDIO_ATTEMPTS = 3
        const val AUDIO_RETRY_DELAY_MS = 3_000L
    }
}
