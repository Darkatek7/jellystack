package dev.jellystack.design.tv

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.profile.HouseholdProfile
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvProfileScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pickerExposesSelectRemoveAndAddAsRealActions() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            JellystackTvTheme {
                TvProfilePickerScreen(
                    profiles = listOf(profile("alice", "Alice"), profile("bob", "Bob")),
                    strings = strings,
                    onSelect = {},
                    onAdd = {},
                    onRemove = {},
                    onManagePin = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Alice").assertIsFocused()
        composeRule.onNodeWithContentDescription("Alice").performClick()
        composeRule.onNodeWithContentDescription("${strings.removeProfile}: Alice").performClick()
        composeRule.onNodeWithContentDescription(strings.addProfile).performClick()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(7)
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
