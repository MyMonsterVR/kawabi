package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<SearchResultDto> = emptyList(),
)

@Serializable
data class SearchResultDto(
    val title: String,
    val url: String,
    val cover_url: String? = null,
    val source_name: String,
    val alternates: List<SearchAlternateDto>? = null,
)

@Serializable
data class SearchAlternateDto(
    val url: String,
    val source_name: String,
)
