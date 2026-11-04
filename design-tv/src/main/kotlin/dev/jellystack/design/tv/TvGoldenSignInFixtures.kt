@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.jellystack.core.preferences.MotionPreference
import dev.jellystack.core.server.JellyfinSignInMethod

/** Sign-in states drawn by the stateless card; see [TvGoldenFixture]. */
internal val GOLDEN_SIGN_IN_FIXTURES =
    setOf(
        TvGoldenFixture.SIGN_IN_EMPTY,
        TvGoldenFixture.SIGN_IN_QUICK_CONNECT,
        TvGoldenFixture.SIGN_IN_HINT,
        TvGoldenFixture.SIGN_IN_ERROR,
        TvGoldenFixture.SIGN_IN_LONG_MESSAGE,
        TvGoldenFixture.SIGN_IN_PASSWORD,
    )

/** Fixtures that draw a whole screen themselves instead of sitting inside the padded column. */
internal val GOLDEN_FULL_SCREEN_FIXTURES = GOLDEN_PLAYER_FIXTURES + GOLDEN_STATE_FIXTURES + GOLDEN_SIGN_IN_FIXTURES

private const val GOLDEN_SERVER_URL = "https://jellyfin.example.com"

@Composable
internal fun GoldenSignInFixture(
    fixture: TvGoldenFixture,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    val noActions =
        TvConnectionActions({}, { _, _ -> }, {}, {}, {})
    val state =
        when (fixture) {
            TvGoldenFixture.SIGN_IN_QUICK_CONNECT ->
                TvConnectionFormState(baseUrl = GOLDEN_SERVER_URL, waitingCode = "482951")
            TvGoldenFixture.SIGN_IN_HINT ->
                TvConnectionFormState(
                    baseUrl = GOLDEN_SERVER_URL,
                    hint = strings.messages.accountHasProfileFormat.format("alex"),
                    canDismiss = true,
                )
            TvGoldenFixture.SIGN_IN_ERROR ->
                TvConnectionFormState(
                    baseUrl = GOLDEN_SERVER_URL,
                    error = strings.quickConnectDisabled,
                    canDismiss = true,
                )
            TvGoldenFixture.SIGN_IN_LONG_MESSAGE ->
                TvConnectionFormState(
                    baseUrl = GOLDEN_SERVER_URL,
                    error = "${strings.quickConnectDisabled} ${strings.quickConnectFailed} ${strings.enterServerUrl}",
                    canDismiss = true,
                )
            TvGoldenFixture.SIGN_IN_PASSWORD ->
                TvConnectionFormState(
                    method = JellyfinSignInMethod.PASSWORD,
                    baseUrl = GOLDEN_SERVER_URL,
                    username = "alex",
                )
            else -> TvConnectionFormState()
        }
    JellystackTvTheme(motionPreference = MotionPreference.REDUCED) {
        TvConnectionContent(state, noActions, remember { TvConnectionFocus() }, strings, modifier)
    }
}
