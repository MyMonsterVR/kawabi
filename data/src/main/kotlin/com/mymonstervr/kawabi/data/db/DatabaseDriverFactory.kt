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
            }
        },
    )
}
