package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BrowseResponse(
    val results: List<SearchResultDto> = emptyList(),
    val has_next_page: Boolean = false,
)
