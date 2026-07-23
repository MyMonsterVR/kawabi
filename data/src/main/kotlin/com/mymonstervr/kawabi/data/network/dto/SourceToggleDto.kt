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
)

@Serializable
data class SetSourceToggleRequest(
    val key: String,
    val enabled: Boolean,
)
