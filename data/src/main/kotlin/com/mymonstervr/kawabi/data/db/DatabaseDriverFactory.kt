package com.mymonstervr.kawabi.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class DatabaseDriverFactory(private val context: Context) {
    fun create(): SqlDriver = AndroidSqliteDriver(
        schema = KawabiDatabase.Schema,
        context = context,
        name = "kawabi.db",
        callback = object : AndroidSqliteDriver.Callback(KawabiDatabase.Schema) {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
                // Measured precisely, same 56-manga refresh back to back: 15575ms on default
                // (DELETE) journal mode, 15558ms on WAL+synchronous=NORMAL -- functionally
                // identical. Journal-mode/fsync cost is NOT the bottleneck here (ruled out with
                // real on-device data, not guesswork) -- the actual cost is the per-manga diff
                // (a DB read of every existing chapter + Kotlin-side list diffing) running
                // sequentially across the whole batch. Explicit revert (not just omitting the
                // WAL pragma) because a device that went through that A/B test has its file
                // already physically converted to WAL, which persists until told otherwise.
                db.query("PRAGMA journal_mode=DELETE", emptyArray()).use { it.moveToFirst() }
            }
        },
    )
}
