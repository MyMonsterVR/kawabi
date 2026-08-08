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
