package ru.kuznetsov.itstime4service.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(ItsTime4ServiceDatabase.Schema, DATABASE_NAME)
}
