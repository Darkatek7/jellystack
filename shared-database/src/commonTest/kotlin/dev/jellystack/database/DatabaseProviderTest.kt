package dev.jellystack.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeDriverFactory : DriverFactory() {
    override suspend fun createDriver(): SqlDriver = throw NotImplementedError("Not used in test")
}

class DatabaseProviderTest {
    @Test
    fun dispatcherDefaults() =
        runTest {
            val provider = DatabaseProvider(FakeDriverFactory())
            assertEquals(expected = kotlinx.coroutines.Dispatchers.Default, actual = provider.dispatcher)
        }
}
