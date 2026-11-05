package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SourceTogglesResponse(
    val sources: List<SourceToggleDto> = emptyList(),
)

@Serializable
data class SourceToggleDto(
    val key: String,
    val name: String,
    val enabled: Boolean,
    val supports_latest: Boolean = true,
    val adult: Boolean = false,
)

@Serializable
data class AdultPrefDto(
    val enabled: Boolean = false,
)

@Serializable
data class SetSourceToggleRequest(
    val key: String,
    val enabled: Boolean,
)
