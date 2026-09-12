package com.mymonstervr.kawabi.domain.model

/**
 * Cross-source identity for an anime (PLAN-anime.md section 18). Keys are
 * `<sourceId>:<url>`, so the same show reached from two sources has two keys and would
 * otherwise become two library rows. Two anime are the same show when they share a MAL id
 * or when their [normalizeAnimeTitle] values match -- the MAL id is the strong signal
 * (present once a tracker link or a tracker import supplied it), the normalized title the
 * only one available for everything else.
 */
data class AnimeIdentity(val malId: String?, val normalizedTitle: String) {
    fun matches(other: AnimeIdentity): Boolean {
        if (malId != null && malId == other.malId) return true
        return normalizedTitle.isNotBlank() && normalizedTitle == other.normalizedTitle
    }
}

fun animeIdentityOf(title: String, malId: String? = null): AnimeIdentity =
    AnimeIdentity(malId?.takeIf { it.isNotBlank() }, normalizeAnimeTitle(title))
