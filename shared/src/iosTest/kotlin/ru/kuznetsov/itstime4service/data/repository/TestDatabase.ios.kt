package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import kotlin.random.Random

internal actual fun testDriver(): SqlDriver = NativeSqliteDriver(
    DatabaseConfiguration(
        name = "test-${Random.nextLong()}",
        version = 1,
        create = {},
        upgrade = { _, _, _ -> },
        inMemory = true,
    ),
)
