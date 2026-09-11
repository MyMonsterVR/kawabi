package com.mymonstervr.kawabi.domain.model

/**
 * Which media axis a tracker/backend call is about. Manga is the default everywhere and
 * deliberately sends nothing on the wire (the backend treats a missing `type` as manga,
 * so existing manga requests stay byte-identical); anime sends `type=anime`.
 */
enum class MediaType(val wireValue: String?) {
    MANGA(null),
    ANIME("anime"),
}
