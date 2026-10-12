package ru.kuznetsov.pitstop.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform SQLite driver for the app database.
 * The database file is created with the current schema on first use.
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

internal const val DATABASE_NAME = "pitstop.db"
