package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.dto.AnimeSourceDto
import com.mymonstervr.kawabi.data.network.dto.SourceToggleDto
import com.mymonstervr.kawabi.data.settings.AppPreferences

class AdultSources(
    private val sourceApi: SourceApi,
    private val animeApi: AnimeApi,
    private val preferences: AppPreferences,
) {
    suspend fun mangaSources(): Result<List<SourceToggleDto>> =
        sourceApi.getSources().map { response ->
            val adult = response.sources.filter { it.adult }
            preferences.setAdultMangaSources(adult.map { it.key }.toSet())
            adult
        }

    suspend fun animeSources(): Result<List<AnimeSourceDto>> =
        animeApi.getSources().map { response ->
            val adult = response.sources.filter { it.adult }
            preferences.setAdultAnimeSources(adult.flatMap { listOf(it.key, it.id) }.filter { it.isNotBlank() }.toSet())
            adult
        }

    suspend fun isAdultManga(vararg keys: String?): Boolean {
        ensureLoaded()
        val adult = preferences.adultMangaSources()
        return keys.any { it != null && it in adult }
    }

    suspend fun isAdultAnime(animeKey: String, source: String): Boolean {
        ensureLoaded()
        val adult = preferences.adultAnimeSources()
        return animeKey.substringBefore(':') in adult || source in adult
    }

    private suspend fun ensureLoaded() {
        if (preferences.adultSourcesKnown()) return
        mangaSources()
        animeSources()
    }
}
