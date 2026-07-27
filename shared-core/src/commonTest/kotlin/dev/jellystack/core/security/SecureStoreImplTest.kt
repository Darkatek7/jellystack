package dev.jellystack.core.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class SecureStoreImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val logger = RecordingSecureStoreLogger()
    private val engine = FakeSecureStoreEngine()
    private val store = SecureStoreImpl(engine, dispatcher, logger)

    @Test
    fun writePersistsValueAndRedactsLogs() =
        runTest(dispatcher) {
            store.write("dummy-token", secretValue("dummy-secret"))

            assertEquals("dummy-secret", engine.data["dummy-token"])
            val event = logger.last()
            assertEquals(SecureStoreAction.WRITE, event.action)
            assertTrue(event.success)
            assertEquals("██", event.redactedValue)
        }

    @Test
    fun readReturnsSecretValue() =
        runTest(dispatcher) {
            engine.data["dummy-token"] = "dummy-cached-value"

            val value = store.read("dummy-token")

            assertEquals("dummy-cached-value", value?.reveal())
            val event = logger.last()
            assertEquals(SecureStoreAction.READ, event.action)
            assertTrue(event.success)
        }

    @Test
    fun readMissingReturnsNull() =
        runTest(dispatcher) {
            val value = store.read("missing")

            assertNull(value)
            val event = logger.last()
            assertEquals(SecureStoreAction.READ, event.action)
            assertTrue(event.success)
        }

    @Test
    fun removeFailureWrapsInSecureStoreException() =
        runTest(dispatcher) {
            engine.shouldFailDelete = true

            try {
                store.remove("dummy-token")
                fail("Expected SecureStoreException")
            } catch (exception: SecureStoreException) {
                assertEquals(SecureStoreAction.REMOVE, exception.action)
                assertEquals("dummy-token", exception.key)
                assertTrue(exception.cause is IllegalStateException)
            }

            val event = logger.last()
            assertEquals(SecureStoreAction.REMOVE, event.action)
            assertTrue(!event.success)
        }

    private class FakeSecureStoreEngine : SecureStoreEngine {
        val data = mutableMapOf<String, String>()
        var shouldFailWrite = false
        var shouldFailDelete = false

        override fun write(
            key: String,
            value: String,
        ) {
            if (shouldFailWrite) throw IllegalStateException("write failure")
            data[key] = value
        }

        override fun read(key: String): String? = data[key]

        override fun delete(key: String) {
            if (shouldFailDelete) throw IllegalStateException("delete failure")
            data.remove(key)
        }
    }

    private class RecordingSecureStoreLogger : SecureStoreLogger {
        private val events = mutableListOf<Event>()

        override fun onSuccess(
            action: SecureStoreAction,
            key: String,
            redactedValue: String?,
        ) {
            events += Event(action, key, true, redactedValue)
        }

        override fun onFailure(
            action: SecureStoreAction,
            key: String,
            throwable: Throwable,
        ) {
            events += Event(action, key, false, throwable = throwable)
        }

        fun last(): Event = events.lastOrNull() ?: error("No events recorded")

        data class Event(
            val action: SecureStoreAction,
            val key: String,
            val success: Boolean,
            val redactedValue: String? = null,
            val throwable: Throwable? = null,
        )
    }
}
