package dev.jellystack.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect open class DriverFactory constructor() {
    open suspend fun createDriver(): SqlDriver
}

class DatabaseProvider(
    private val driverFactory: DriverFactory,
) {
    val dispatcher: CoroutineDispatcher = Dispatchers.Default

    suspend fun database() = JellystackDatabase(driverFactory.createDriver())
}
