package dev.jellystack.design

import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TutorialNavigationTest {
    @Test
    fun explicitBackKeepsTheConnectedJellyfinStepVisible() {
        val destination =
            tutorialAutoAdvanceDestination(
                step = TutorialStep.ConnectJellyfin,
                jellyfinConnected = true,
                seerrConnected = false,
                automaticAdvanceAllowed = false,
            )

        assertNull(destination)
    }

    @Test
    fun existingConnectionsStillAdvanceDuringNormalOnboarding() {
        assertEquals(
            TutorialStep.ConnectJellyseerr,
            tutorialAutoAdvanceDestination(
                step = TutorialStep.ConnectJellyfin,
                jellyfinConnected = true,
                seerrConnected = false,
                automaticAdvanceAllowed = true,
            ),
        )
        assertEquals(
            TutorialStep.Explore,
            tutorialAutoAdvanceDestination(
                step = TutorialStep.ConnectJellyseerr,
                jellyfinConnected = true,
                seerrConnected = true,
                automaticAdvanceAllowed = true,
            ),
        )
    }

    @Test
    fun returningToJellyfinHydratesAnEditFormWithTheExistingServerId() {
        val server =
            ManagedServer(
                id = "jellyfin-1",
                type = ServerType.JELLYFIN,
                name = "Home Jellyfin",
                baseUrl = "http://jellyfin.example:8096",
                credentials = StoredCredential.Jellyfin("dummy-user", null, "dummy-token", "user-1"),
                createdAt = Instant.fromEpochMilliseconds(1L),
                updatedAt = Instant.fromEpochMilliseconds(1L),
            )

        val form =
            onboardingServerForm(
                step = TutorialStep.ConnectJellyfin,
                current = ServerFormState(),
                activeJellyfin = server,
                activeSeerr = null,
            )

        assertEquals("jellyfin-1", form.serverId)
        assertEquals("Home Jellyfin", form.name)
        assertEquals("http://jellyfin.example:8096", form.baseUrl)
        assertEquals("dummy-user", form.username)
    }

    @Test
    fun returningFromReadyRestoresTheRememberedSeerrForm() {
        val remembered =
            ServerFormState(
                type = ServerFormType.SEERR,
                name = "Family Seerr",
                baseUrl = "https://requests.example",
                automaticSeerrLogin = true,
                useJellyfinLogin = true,
            )

        val form =
            onboardingServerForm(
                step = TutorialStep.ConnectJellyseerr,
                rememberedForms = mapOf(TutorialStep.ConnectJellyseerr to remembered),
                activeJellyfin = null,
                activeSeerr = null,
            )

        assertEquals(remembered, form)
    }
}
