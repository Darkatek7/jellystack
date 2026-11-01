package dev.jellystack.database

import app.cash.sqldelight.db.SqlDriver

expect open class DriverFactory constructor() {
    open suspend fun createDriver(): SqlDriver
}
