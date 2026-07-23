package com.mymonstervr.kawabi.domain.model

data class History(
    val id: Long,
    val chapterId: Long,
    val lastRead: Long?,
    val timeRead: Long,
)
