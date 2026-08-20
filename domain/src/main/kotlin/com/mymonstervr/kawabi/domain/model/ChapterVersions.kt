package com.mymonstervr.kawabi.domain.model

/**
 * Some sources (MangaFire confirmed; others may follow) list two or more versions of
 * the same chapter number under different `scanlator` values -- e.g. "official" vs
 * "unofficial". Values aren't a fixed enum: MangaFire uses lowercase official/
 * unofficial, other sources use real scanlation-group names or leave it blank when
 * there's only ever one version. These helpers treat scanlator as an opaque string
 * and only ever engage when a manga *actually* has duplicated chapter numbers with
 * differing scanlators -- everything else renders exactly as it did before this
 * feature existed.
 */

/** Case-insensitive identity used to decide whether two scanlator values are "the same". */
fun String?.normalizedScanlator(): String? = this?.trim()?.ifBlank { null }?.lowercase()

/** One selectable version of a manga's chapters, e.g. "Official" (23 chapters). */
data class ChapterVersionOption(
    val scanlator: String?,
    val displayLabel: String,
    val count: Int,
)

/**
 * The set of distinct versions worth offering a picker for -- only scanlators that
 * appear on a chapter number shared with at least one *other* differing scanlator.
 * A manga where every chapter has a unique number (no duplicates) or where all
 * duplicates share the same scanlator returns an empty list.
 */
fun List<Chapter>.chapterVersionOptions(): List<ChapterVersionOption> {
    val duplicatedNumbers = groupBy { it.chapterNumber }
        .filterValues { group -> group.map { it.scanlator.normalizedScanlator() }.distinct().size > 1 }
        .keys
    if (duplicatedNumbers.isEmpty()) return emptyList()

    return filter { it.chapterNumber in duplicatedNumbers }
        .groupBy { it.scanlator.normalizedScanlator() }
        .map { (normalized, group) ->
            ChapterVersionOption(
                scanlator = normalized,
                displayLabel = group.firstNotNullOfOrNull { it.scanlator?.trim()?.ifBlank { null } }
                    ?.replaceFirstChar { c -> c.titlecase() }
                    ?: "Unknown",
                count = group.size,
            )
        }
        .sortedByDescending { it.count }
}

/** True once a manga has at least two distinct versions worth offering a choice between. */
fun List<Chapter>.hasMultipleChapterVersions(): Boolean = chapterVersionOptions().size > 1

/** Badge text for one chapter row -- only meaningful to show when the manga is multi-version. */
fun Chapter.versionBadgeLabel(): String =
    scanlator?.trim()?.ifBlank { null }?.replaceFirstChar { it.titlecase() } ?: "Unknown"

/** Renders a chapter number without a trailing ".0" for whole numbers (e.g. "12" not "12.5" -> "12"). */
fun formatChapterNumber(number: Double): String =
    if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()

/**
 * Renders a chapter's upload timestamp as a short relative label (e.g. "3d ago").
 * Returns null when there's no usable date -- sources like Asura/mangafire mirror
 * this reliably, but not every Suwayomi-backed source populates it.
 */
fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String? {
    if (epochMillis <= 0) return null
    val diffMs = (now - epochMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
