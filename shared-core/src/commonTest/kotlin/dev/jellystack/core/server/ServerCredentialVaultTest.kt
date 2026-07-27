package dev.jellystack.core.server

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.security.secretValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerCredentialVaultTest {
    @Test
    fun saveWritesPrimaryKeyAndClearsLegacyEntries() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.password", secretValue("dummy-legacy-password"))

            vault.saveJellyfinPassword("demo", "dummy-new-password")

            assertEquals(
                "dummy-new-password",
                secureStore.peek("servers.demo.jellyfin.password")?.reveal(),
            )
            assertNull(secureStore.peek("servers.demo.password"))
            assertNull(secureStore.peek("servers.demo.jellyfinPassword"))
        }

    @Test
    fun readReturnsPrimarySecretWhenPresent() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.jellyfin.password", secretValue("dummy-stored-password"))

            val secret = vault.readJellyfinPassword("demo")

            assertEquals("dummy-stored-password", secret?.reveal())
        }

    @Test
    fun readMigratesLegacySecretToPrimaryKey() =
        runTest {
            val secureStore = FakeSecureStore()
            val vault = ServerCredentialVault(secureStore)
            secureStore.write("servers.demo.jellyfinPassword", secretValue("dummy-legacy-password"))

            val secret = vault.readJellyfinPassword("demo")

            assertEquals("dummy-legacy-password", secret?.reveal())
            assertEquals(
                "dummy-legacy-password",
                secureStore.peek("servers.demo.jellyfin.password")?.reveal(),
            )
            assertNull(secureStore.peek("servers.demo.jellyfinPassword"))
        }
}
