package com.mymonstervr.kawabi.domain.model

// Kotlin port of the backend's anime.NormalizeAnimeTitle (mihon-sync-server
// internal/anime/match.go) plus the source.NormalizeTitle it builds on. Kept
// rule-for-rule identical so a title the backend considers the same show is the same
// show here: casing/punctuation collapse, a dropped leading English article, the
// sub/dub-style qualifiers removed, and every season spelling folded to "season-<n>".
// A season number is kept, never dropped -- "X Season 2" and "X" are different shows.

private val qualifierRegex = Regex("""\((?:tv|dub|dubbed|sub|subbed|subs|uncensored|uncut|censored)\)""")
private val nonAlphanumericRegex = Regex("[^a-z0-9]+")
private val leadingArticleRegex = Regex("^(the|an|a)-")
private val shortSeasonRegex = Regex("^s([1-9])$")

private val ordinalNumbers = mapOf(
    "1st" to 1, "first" to 1,
    "2nd" to 2, "second" to 2,
    "3rd" to 3, "third" to 3,
    "4th" to 4, "fourth" to 4,
    "5th" to 5, "fifth" to 5,
    "6th" to 6, "sixth" to 6,
    "7th" to 7, "seventh" to 7,
    "8th" to 8, "eighth" to 8,
    "9th" to 9, "ninth" to 9,
)

// "i" is deliberately absent: it is an ordinary English word.
private val romanNumbers = mapOf(
    "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9,
)

// "cour" is absent on purpose -- a cour is half a season, so folding it would mean
// something else entirely.
private val seasonWords = setOf("season", "part")

fun normalizeAnimeTitle(title: String): String {
    val lowered = qualifierRegex.replace(title.lowercase(), " ")
    val dashed = nonAlphanumericRegex.replace(lowered, "-").trim('-')
    return unifySeasons(leadingArticleRegex.replace(dashed, ""))
}

private fun unifySeasons(normalized: String): String {
    if (normalized.isEmpty()) return ""
    val tokens = normalized.split('-')
    val out = mutableListOf<String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        val next = tokens.getOrElse(i + 1) { "" }
        val seasonNumber = tokenNumber(next)
        val ordinal = ordinalNumbers[token]
        val shortSeason = shortSeasonRegex.find(token)?.groupValues?.get(1)
        val trailingRoman = romanNumbers[token]?.takeIf { i == tokens.lastIndex }
        when {
            token in seasonWords && seasonNumber != null -> {
                out += listOf("season", seasonNumber.toString())
                i++
            }
            ordinal != null && next in seasonWords -> {
                out += listOf("season", ordinal.toString())
                i++
            }
            shortSeason != null -> out += listOf("season", shortSeason)
            trailingRoman != null -> out += listOf("season", trailingRoman.toString())
            else -> out += token
        }
        i++
    }
    return out.joinToString("-")
}

private fun tokenNumber(token: String): Int? =
    token.toIntOrNull()?.takeIf { it in 1..9 } ?: romanNumbers[token]
