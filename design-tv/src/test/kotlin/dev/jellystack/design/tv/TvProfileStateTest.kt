package dev.jellystack.design.tv

import dev.jellystack.core.profile.HouseholdProfile
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TvProfileStateTest {
    @Test
    fun zeroProfilesNeedsConnectionAndOneProfileActivatesDirectly() {
        assertEquals(
            TvProfileState.NeedsConnection,
            initialTvProfileState(emptyList(), coldLaunch = true, pickerWasVisible = false, rememberedProfileId = null),
        )
        assertIs<TvProfileState.Content>(
            initialTvProfileState(
                listOf(PROFILE_A),
                coldLaunch = true,
                pickerWasVisible = false,
                rememberedProfileId = null,
            ),
        )
    }

    @Test
    fun singlePinProtectedProfileAsksForItsPinOnColdLaunchButNotOnResume() {
        val pinProtected = setOf(PROFILE_A.id)

        assertEquals(
            TvProfileState.PinEntry(PROFILE_A),
            initialTvProfileState(
                listOf(PROFILE_A),
                coldLaunch = true,
                pickerWasVisible = false,
                rememberedProfileId = PROFILE_A.id,
            ).lockedOnColdLaunch(coldLaunch = true, pinProtectedProfileIds = pinProtected),
        )
        assertEquals(
            TvProfileState.Content(PROFILE_A, 2),
            initialTvProfileState(
                listOf(PROFILE_A),
                coldLaunch = false,
                pickerWasVisible = false,
                rememberedProfileId = PROFILE_A.id,
                generation = 2,
            ).lockedOnColdLaunch(coldLaunch = false, pinProtectedProfileIds = pinProtected),
        )
    }

    @Test
    fun profileWithoutPinStillOpensDirectlyOnColdLaunch() {
        assertEquals(
            TvProfileState.Content(PROFILE_B, 0),
            initialTvProfileState(
                listOf(PROFILE_B),
                coldLaunch = true,
                pickerWasVisible = false,
                rememberedProfileId = PROFILE_B.id,
            ).lockedOnColdLaunch(coldLaunch = true, pinProtectedProfileIds = setOf(PROFILE_A.id)),
        )
    }

    @Test
    fun multipleProfilesShowPickerOnColdLaunch() {
        assertIs<TvProfileState.Picker>(
            initialTvProfileState(
                listOf(PROFILE_A, PROFILE_B),
                coldLaunch = true,
                pickerWasVisible = false,
                rememberedProfileId = PROFILE_A.id,
            ),
        )
    }

    @Test
    fun backgroundResumeRetainsActiveProfile() {
        val result =
            initialTvProfileState(
                listOf(PROFILE_A, PROFILE_B),
                coldLaunch = false,
                pickerWasVisible = false,
                rememberedProfileId = PROFILE_B.id,
                generation = 4,
            )

        assertEquals(TvProfileState.Content(PROFILE_B, 4), result)
    }

    @Test
    fun processDeathAtPickerRestoresPickerInsteadOfRememberedProfile() {
        assertIs<TvProfileState.Picker>(
            initialTvProfileState(
                listOf(PROFILE_A, PROFILE_B),
                coldLaunch = false,
                pickerWasVisible = true,
                rememberedProfileId = PROFILE_A.id,
            ),
        )
    }

    @Test
    fun avatarIsOutsideTheFiveNavigationDestinations() {
        assertEquals(5, TV_TOP_LEVEL_DESTINATION_COUNT)
        assertEquals("rail:profile-avatar", TV_PROFILE_AVATAR_TARGET)
        assertEquals(false, TV_PROFILE_AVATAR_TARGET.startsWith("rail:home"))
    }

    private companion object {
        val PROFILE_A = profile("profile-a", "Alice")
        val PROFILE_B = profile("profile-b", "Bob")

        fun profile(
            id: String,
            name: String,
        ) = HouseholdProfile(
            id = id,
            displayName = name,
            avatarSeed = id,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
    }
}
