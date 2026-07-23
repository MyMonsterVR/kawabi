package com.mymonstervr.kawabi.domain.model

/**
 * A chapter as reported fresh by a source right now -- the diffing input
 * for [com.mymonstervr.kawabi.domain.interactor.SyncChaptersWithSource].
 * `url` is the chapter's stable identity key (backend's chapter id).
 */
data class SourceChapter(
    val url: String,
    val name: String,
    val chapterNumber: Double,
    val dateUpload: Long,
)
