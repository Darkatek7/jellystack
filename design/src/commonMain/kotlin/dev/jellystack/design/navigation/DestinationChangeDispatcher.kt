package dev.jellystack.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

internal class DestinationChangeDispatcher(
    private val clearFocus: () -> Unit,
    private val hideKeyboard: () -> Unit,
) {
    fun dispatch(change: () -> Unit) {
        clearFocus()
        hideKeyboard()
        change()
    }

    fun action(change: () -> Unit): () -> Unit = { dispatch(change) }

    fun <T> callback(change: (T) -> Unit): (T) -> Unit =
        { value ->
            dispatch { change(value) }
        }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun rememberDestinationChangeDispatcher(): DestinationChangeDispatcher {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        DestinationChangeDispatcher(
            clearFocus = { focusManager.clearFocus(force = true) },
            hideKeyboard = { keyboard?.hide() },
        )
    }
}
