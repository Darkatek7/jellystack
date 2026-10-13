package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Focus for a dialog's default action. A dialog opens in its own window without focus, so without this the first
 * D-pad press only moves focus into the dialog. Focus is requested once the dialog has been placed.
 */
@Composable
internal fun rememberTvDialogInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        withFrameNanos { }
        // The requester is unattached when the dialog hides its default action; focus then stays where it is.
        runCatching { requester.requestFocus() }
    }
    return requester
}
