package ru.kuznetsov.itstime4service.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

internal actual fun testDriver(): SqlDriver {
    Class.forName(SQLITE_JDBC_DRIVER)
    val file = File.createTempFile("itstime4service-test", ".db").apply { deleteOnExit() }
    val properties = Properties().apply {
        put("journal_mode", "WAL")
        put("busy_timeout", "5000")
    }
    return JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties)
}

private const val SQLITE_JDBC_DRIVER = "org.sqlite.JDBC"
