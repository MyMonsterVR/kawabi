package com.mymonstervr.kawabi.data.track

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.domain.model.MediaType

/**
 * Public list-entry url for a tracked title. Shared by the manga and anime sync clients --
 * MAL, Kitsu and AniList all split their catalogs by media type in the path, so the only thing
 * that varies between the two axes is that one path segment.
 */
fun trackingUrlFor(trackerId: String, remoteId: String, mediaType: MediaType = MediaType.MANGA): String {
    val segment = if (mediaType == MediaType.ANIME) "anime" else "manga"
    return when (trackerId) {
        TrackerTokenStore.TRACKER_MAL -> "https://myanimelist.net/$segment/$remoteId"
        TrackerTokenStore.TRACKER_KITSU -> "https://kitsu.app/$segment/$remoteId"
        TrackerTokenStore.TRACKER_ANILIST -> "https://anilist.co/$segment/$remoteId"
        else -> error("Unknown tracker: $trackerId")
    }
}
