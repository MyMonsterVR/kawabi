package com.mymonstervr.kawabi.data.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver

// ASCII unit separator (0x1F) -- won't collide with real genre names.
private const val GENRE_SEPARATOR = ""

val genreListAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String): List<String> =
        if (databaseValue.isEmpty()) emptyList() else databaseValue.split(GENRE_SEPARATOR)

    override fun encode(value: List<String>): String = value.joinToString(GENRE_SEPARATOR)
}

fun createDatabase(driver: SqlDriver): KawabiDatabase = KawabiDatabase(
    driver = driver,
    mangasAdapter = Mangas.Adapter(genreAdapter = genreListAdapter),
)
