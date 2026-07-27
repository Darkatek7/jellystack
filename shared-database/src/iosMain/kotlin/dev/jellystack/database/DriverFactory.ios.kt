package dev.jellystack.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual open class DriverFactory actual constructor() {
    actual open suspend fun createDriver(): SqlDriver = NativeSqliteDriver(JellystackDatabase.Schema, "jellystack.db")
}
