package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.db.SqlDriver
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase

/**
 * An empty SQLite database for repository and view model tests: [driver] has no tables yet; [create] builds the current
 * schema, [database] wraps the driver for the generated queries. Each instance is a separate database, safe to read
 * and write from several threads at once, as the repositories do.
 */
internal class TestDatabase {
    val driver: SqlDriver = testDriver()

    val database: ItsTime4ServiceDatabase by lazy { ItsTime4ServiceDatabase(driver) }

    fun create(): TestDatabase {
        ItsTime4ServiceDatabase.Schema.create(driver)
        return this
    }

    fun execute(sql: String) {
        driver.execute(null, sql, 0)
    }
}

internal expect fun testDriver(): SqlDriver
