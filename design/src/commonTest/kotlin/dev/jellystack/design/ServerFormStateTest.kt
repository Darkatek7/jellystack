package dev.jellystack.design

import dev.jellystack.core.server.JellyfinSignInMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerFormStateTest {
    @Test
    fun jellyseerrFormValidWithCredentials() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                baseUrl = "https://requests.example",
                email = "user@example.com",
                password = "dummy-credential",
            )

        assertTrue(state.isValid)
    }

    @Test
    fun jellyseerrFormInvalidWithoutEmail() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                baseUrl = "https://requests.example",
                password = "dummy-credential",
            )

        assertFalse(state.isValid)
    }

    @Test
    fun jellyseerrFormValidWithJellyfinLogin() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                baseUrl = "https://requests.example",
                username = "dummy-user",
                password = "dummy-credential",
                useJellyfinLogin = true,
            )

        assertTrue(state.isValid)
    }

    @Test
    fun jellyseerrFormValidWithPasswordlessJellyfinLogin() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                name = "Seerr",
                baseUrl = "https://requests.example",
                username = "passwordless-user",
                password = "",
                useJellyfinLogin = true,
            )

        assertTrue(state.isValid)
    }

    @Test
    fun jellyseerrFallbackRequiresTheJellyfinPassword() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                name = "Seerr",
                baseUrl = "https://requests.example",
                username = "passwordless-user",
                password = "",
                useJellyfinLogin = true,
                requiresSeerrPassword = true,
            )

        assertFalse(state.isValid)
        assertTrue(state.copy(password = "dummy-credential").isValid)
    }

    @Test
    fun jellyseerrFormInvalidWithoutUsernameForJellyfinLogin() {
        val state =
            ServerFormState(
                type = ServerFormType.SEERR,
                baseUrl = "https://requests.example",
                password = "dummy-credential",
                useJellyfinLogin = true,
            )

        assertFalse(state.isValid)
    }

    @Test
    fun quickConnectIsDefaultAndNeedsOnlyServerDetails() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.example",
            )

        assertTrue(state.isValid)
    }

    @Test
    fun passwordFallbackAcceptsPasswordlessJellyfinAccount() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.example",
                username = "dummy-user",
                jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
            )

        assertTrue(state.isValid)
    }

    @Test
    fun passwordFallbackStillRequiresUsername() {
        val state =
            ServerFormState(
                type = ServerFormType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.example",
                password = "dummy-credential",
                jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
            )

        assertFalse(state.isValid)
    }

    @Test
    fun newHttpServerRequiresExplicitRiskConfirmation() {
        val unconfirmed =
            ServerFormState(
                type = ServerFormType.JELLYFIN,
                name = "Local demo",
                baseUrl = "http://media.example",
            )

        assertTrue(unconfirmed.requiresInsecureHttpConfirmation)
        assertFalse(unconfirmed.isValid)
        assertTrue(unconfirmed.copy(allowInsecureHttp = true).isValid)
    }

    @Test
    fun existingHttpServerCanStillBeEditedWithoutReconfirmation() {
        val existing =
            ServerFormState(
                serverId = "jellyfin-1",
                type = ServerFormType.JELLYFIN,
                name = "Local demo",
                baseUrl = "http://media.example",
            )

        assertFalse(existing.requiresInsecureHttpConfirmation)
        assertTrue(existing.isValid)
    }
}
