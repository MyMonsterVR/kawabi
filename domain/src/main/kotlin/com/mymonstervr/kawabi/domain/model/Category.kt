package com.mymonstervr.kawabi.domain.model

data class Category(
    val id: Long,
    val name: String,
    val sort: Int,
    val flags: Int,
)
