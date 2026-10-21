package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.db.SqlDriver
import ru.kuznetsov.itstime4service.data.local.ItsTime4ServiceDatabase

/**
 * An empty SQLite database in memory for repository tests: [driver] has no tables yet; [create] builds the current
 * schema, [database] wraps the driver for the generated queries. Each instance is a separate database.
 */
internal class TestDatabase {
    val driver: SqlDriver = inMemoryDriver()

    val database: ItsTime4ServiceDatabase by lazy { ItsTime4ServiceDatabase(driver) }

    fun create(): TestDatabase {
        ItsTime4ServiceDatabase.Schema.create(driver)
        return this
    }

    fun execute(sql: String) {
        driver.execute(null, sql, 0)
    }
}

internal expect fun inMemoryDriver(): SqlDriver
