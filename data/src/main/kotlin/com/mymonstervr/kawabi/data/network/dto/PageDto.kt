package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    val index: Int,
    val proxied_image_url: String,
    val offset: Int? = null,
    val tiles: List<Int>? = null,
    val tile_cols: Int? = null,
    val tile_rows: Int? = null,
)
