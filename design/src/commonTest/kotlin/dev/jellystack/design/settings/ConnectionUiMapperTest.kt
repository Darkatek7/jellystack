package dev.jellystack.design.settings

import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionUiMapperTest {
    @Test
    fun mapperExposesNoUrlOrCredentialMaterial() {
        val server =
            ManagedServer(
                id = "server-1",
                type = ServerType.JELLYFIN,
                name = "Living Room",
                baseUrl = "https://secret.example",
                credentials =
                    StoredCredential.Jellyfin(
                        username = "owner",
                        deviceId = "device-secret",
                        accessToken = "dummy-token-do-not-render",
                        userId = "user-secret",
                    ),
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            )

        val ui =
            server.toSettingsConnectionUi(
                isActive = true,
                health = SettingsConnectionHealth.Ready,
            )

        assertEquals("server-1", ui.id)
        assertEquals("Living Room", ui.name)
        assertTrue(ui.isActive)
        assertFalse(ui.toString().contains("secret.example"))
        assertFalse(ui.toString().contains("dummy-token-do-not-render"))
        assertFalse(ui.toString().contains("device-secret"))
        assertFalse(ui.toString().contains("user-secret"))
    }
}
