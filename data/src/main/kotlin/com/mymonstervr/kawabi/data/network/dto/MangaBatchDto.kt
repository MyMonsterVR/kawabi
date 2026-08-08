package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaBatchRequest(val urls: List<String>)

@Serializable
data class MangaBatchResult(
    val url: String,
    val manga: MangaResponse? = null,
    val error: String? = null,
)

@Serializable
data class MangaBatchResponse(val results: List<MangaBatchResult>)
