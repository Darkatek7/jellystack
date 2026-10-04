package dev.jellystack.design.tv

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.profile.HouseholdProfile
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvProfileScreensTest {
    @get:Rule
    val composeRule = createTvComposeRule()

    @Test
    fun pickerExposesOnlySelectionAddAndManageActions() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvProfilePickerScreen(
                    profiles = listOf(presentation("alice", "Alice"), presentation("bob", "Bob")),
                    rememberedProfileId = "bob",
                    strings = strings,
                    onSelect = {},
                    onAdd = {},
                    onManage = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile:bob:tile").assertIsFocused().performClick()
        composeRule.onNodeWithContentDescription(strings.addProfile).assertHasClickAction()
        composeRule.onNodeWithContentDescription(strings.manageProfiles).assertHasClickAction()
        composeRule.onNodeWithText(strings.removeProfile).assertDoesNotExist()
        composeRule.onNodeWithText(strings.profilePin).assertDoesNotExist()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(4)
    }

    @Test
    fun deleteConfirmationStatesThatServerAccountIsPreserved() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvRemoveProfileDialog(profile("alice", "Alice"), strings, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(strings.removeProfileMessage).assertExists()
        composeRule.onNodeWithContentDescription(strings.removeProfile).performClick()
    }

    @Test
    fun pickerUsesTheBoundJellyfinUserAvatarWhenAvailable() {
        composeRule.setContent {
            JellystackTvTheme {
                TvProfilePickerScreen(
                    profiles = listOf(presentation("alice", "Alice", avatarUrl = "file:///missing-test-avatar.png")),
                    rememberedProfileId = null,
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    onSelect = {},
                    onAdd = {},
                    onManage = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("profile:alice:avatar-image", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Alice").assertExists()
        composeRule.onNodeWithTag("profile:alice:avatar-fallback", useUnmergedTree = true).assertExists()
    }

    @Test
    fun pickerFocusTargetIsTheCircularAvatarRatherThanTheWholeLabelTile() {
        composeRule.setContent {
            JellystackTvTheme {
                TvProfilePickerScreen(
                    profiles = listOf(presentation("alice", "Alice")),
                    rememberedProfileId = "alice",
                    strings = TvStrings.current(AppLanguage.ENGLISH),
                    onSelect = {},
                    onAdd = {},
                    onManage = {},
                )
            }
        }

        val bounds = composeRule.onNodeWithTag("profile:alice:tile").assertIsFocused().getUnclippedBoundsInRoot()
        assertEquals((bounds.right - bounds.left).value, (bounds.bottom - bounds.top).value, 0.01f)
    }

    @Test
    fun profileManagementKeepsPinAndRemovalOutOfTheGrid() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvProfileManagementScreen(
                    profiles = listOf(presentation("alice", "Alice"), presentation("bob", "Bob")),
                    selectedProfileId = null,
                    strings = strings,
                    onSelectProfile = {},
                    onManagePin = {},
                    onRemove = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("profile-management:alice:tile").assertExists()
        composeRule.onNodeWithContentDescription("${strings.profilePin}: Alice").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("${strings.removeProfile}: Alice").assertDoesNotExist()
    }

    @Test
    fun selectedManagementProfileExposesOnlyItsValidActions() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvProfileManagementScreen(
                    profiles = listOf(presentation("alice", "Alice"), presentation("bob", "Bob")),
                    selectedProfileId = "alice",
                    strings = strings,
                    onSelectProfile = {},
                    onManagePin = {},
                    onRemove = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("${strings.profilePin}: Alice").assertHasClickAction()
        composeRule.onNodeWithContentDescription("${strings.removeProfile}: Alice").assertHasClickAction()
        composeRule.onNodeWithContentDescription("${strings.profilePin}: Bob").assertDoesNotExist()
    }

    private fun presentation(
        id: String,
        name: String,
        avatarUrl: String? = null,
    ) = TvProfilePresentation(
        id = id,
        displayName = name,
        avatarUrl = avatarUrl,
        pinRequired = false,
        lastActiveAt = Instant.fromEpochMilliseconds(0),
    )

    private fun profile(
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
