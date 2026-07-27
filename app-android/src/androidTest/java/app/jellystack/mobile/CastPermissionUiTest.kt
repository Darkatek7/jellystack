package app.jellystack.mobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jellystack.mobile.cast.CastPermissionUiState
import app.jellystack.mobile.cast.CastPickerHost
import app.jellystack.mobile.cast.PendingCastPicker
import app.jellystack.mobile.ui.PermissionAwareCastRouteButton
import dev.jellystack.players.cast.CastConnectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CastPermissionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstTapExplainsAccessBeforeLaunchingPermissionRequest() {
        var requests = 0
        var state by
            mutableStateOf(
                CastPermissionUiState(granted = false, requested = false, rationale = false),
            )
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    PermissionAwareCastRouteButton(
                        castState = CastConnectionState.Idle,
                        permissionState = state,
                        host = CastPickerHost.Shell,
                        onAction = {
                            state = state.copy(pendingPicker = PendingCastPicker(1L, it))
                        },
                        onRequestPermissions = { requests++ },
                        onOpenSettings = {},
                        onPickerConsumed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Cast, not connected").performClick()
        composeRule.onNodeWithText("Find nearby Cast devices").assertExists()
        composeRule.onNodeWithText("Continue").performClick()

        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun permanentlyDeniedAccessOffersAppSettings() {
        var settingsOpens = 0
        var state by
            mutableStateOf(
                CastPermissionUiState(granted = false, requested = true, rationale = false),
            )
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    PermissionAwareCastRouteButton(
                        castState = CastConnectionState.Idle,
                        permissionState = state,
                        host = CastPickerHost.Shell,
                        onAction = {
                            state = state.copy(pendingPicker = PendingCastPicker(2L, it))
                        },
                        onRequestPermissions = {},
                        onOpenSettings = { settingsOpens++ },
                        onPickerConsumed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Cast, not connected").performClick()
        composeRule.onNodeWithText("Open settings").performClick()

        composeRule.runOnIdle { assertEquals(1, settingsOpens) }
    }

    @Test
    fun connectingCastControlAnnouncesItsState() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    PermissionAwareCastRouteButton(
                        castState = CastConnectionState.Connecting("Living room TV"),
                        permissionState =
                            CastPermissionUiState(
                                granted = false,
                                requested = false,
                                rationale = false,
                            ),
                        host = CastPickerHost.Shell,
                        onAction = {},
                        onRequestPermissions = {},
                        onOpenSettings = {},
                        onPickerConsumed = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Cast, connecting").assertExists()
    }
}
