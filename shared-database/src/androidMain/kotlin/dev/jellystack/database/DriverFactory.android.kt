package dev.jellystack.database

import app.cash.sqldelight.db.SqlDriver

actual open class DriverFactory actual constructor() {
    actual open suspend fun createDriver(): SqlDriver = error("Android driver requires context binding. Provide via platform DI layer.")
}
