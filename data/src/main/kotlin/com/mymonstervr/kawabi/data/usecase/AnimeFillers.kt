package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class FillerEpisodes(val filler: Set<Double> = emptySet(), val mixed: Set<Double> = emptySet()) {
    val any: Boolean get() = filler.isNotEmpty()
}

/**
 * Filler/mixed episode numbers for a show, shared by the details screen and the player.
 * Held in memory for six hours per title set: the list only changes when new episodes air,
 * and both screens ask for it within seconds of each other. A failed lookup is not cached.
 */
class AnimeFillers(private val animeApi: AnimeApi) {
    private val cache = ConcurrentHashMap<String, Pair<Long, FillerEpisodes>>()

    suspend fun get(titles: List<String?>): FillerEpisodes {
        val distinct = titles.filterNotNull().filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return FillerEpisodes()
        val cacheKey = distinct.joinToString("\u0000")
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { (at, value) -> if (now - at < TTL_MS) return value }
        val response = animeApi.getFillers(distinct).getOrNull() ?: return FillerEpisodes()
        val value = FillerEpisodes(response.filler.toSet(), response.mixed.toSet())
        cache[cacheKey] = now to value
        return value
    }

    private companion object {
        val TTL_MS = TimeUnit.HOURS.toMillis(6)
    }
}
