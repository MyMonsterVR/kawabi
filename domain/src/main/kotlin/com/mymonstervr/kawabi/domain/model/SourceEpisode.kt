package com.mymonstervr.kawabi.domain.model

/**
 * An episode as reported fresh by a source right now -- the diffing input for
 * [com.mymonstervr.kawabi.domain.interactor.SyncEpisodesWithSource]. [url] is the stable
 * identity within one anime; [key] is the backend's global episode key.
 */
data class SourceEpisode(
    val key: String,
    val url: String,
    val name: String,
    val episodeNumber: Double,
    val dateUpload: Long,
)
