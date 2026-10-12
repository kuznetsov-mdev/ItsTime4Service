package ru.kuznetsov.pitstop.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(PitStopDatabase.Schema, DATABASE_NAME)
}
