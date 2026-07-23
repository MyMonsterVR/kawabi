package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AltTitlesResponse(val titles: List<String> = emptyList())
