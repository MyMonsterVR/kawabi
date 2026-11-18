package com.mymonstervr.kawabi.domain.model

const val AUDIO_SUB = "sub"
const val AUDIO_DUB = "dub"

// Port of kawabi-web's src/lib/anime-audio.ts -- keep the two in lockstep. An episode's
// audio comes only from the source's tag ("Sub", "Dub", "Sub & Dub"); an empty tag means
// the source doesn't say, which counts as available in sub and not known to be dubbed.

fun scanlatorHasDub(scanlator: String): Boolean = scanlator.contains("dub", ignoreCase = true)

fun scanlatorHasSub(scanlator: String): Boolean = scanlator.isEmpty() || scanlator.contains("sub", ignoreCase = true)

fun scanlatorLacksDub(scanlator: String): Boolean = scanlator.isNotEmpty() && !scanlatorHasDub(scanlator)

val Episode.hasDub: Boolean get() = scanlatorHasDub(scanlator)
val Episode.hasSub: Boolean get() = scanlatorHasSub(scanlator)
val Episode.lacksDub: Boolean get() = scanlatorLacksDub(scanlator)

/** Per-show choice wins, then the global player preference, then sub. */
fun effectiveShowAudio(showAudio: String, globalAudio: String): String =
    showAudio.ifEmpty { globalAudio.ifEmpty { AUDIO_SUB } }

/**
 * Some sources list an episode once per audio (same number, two rows). Keeps only the
 * rows in [audio] for those numbers; a number with a single row, or with no row in
 * [audio] at all, is left as it is. Order is preserved.
 */
fun <T> episodesForAudio(
    episodes: List<T>,
    audio: String,
    numberOf: (T) -> Double,
    scanlatorOf: (T) -> String,
): List<T> {
    val byNumber = episodes.groupBy(numberOf)
    if (byNumber.values.all { it.size == 1 }) return episodes
    val wantsDub = audio == AUDIO_DUB
    val kept = HashSet<T>()
    for ((number, group) in byNumber) {
        if (group.size == 1 || number < 0) {
            kept += group
            continue
        }
        val matching = group.filter { if (wantsDub) scanlatorHasDub(scanlatorOf(it)) else scanlatorHasSub(scanlatorOf(it)) }
        kept += matching.ifEmpty { group }
    }
    return episodes.filter { it in kept }
}

fun List<Episode>.forAudio(audio: String): List<Episode> =
    episodesForAudio(this, audio, { it.episodeNumber }, { it.scanlator })
