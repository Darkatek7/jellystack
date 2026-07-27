package dev.jellystack.design.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first

@Suppress("FunctionName")
@Composable
internal fun ModalFocusScope(
    onDismissRequest: () -> Unit,
    returnFocusRequester: FocusRequester?,
    fullScreen: Boolean = false,
    content: @Composable (initialFocusModifier: Modifier) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val initialFocusTargetPlaced = remember { mutableStateOf(false) }
    val initialFocusModifier =
        Modifier
            .focusRequester(firstFocus)
            .focusProperties { canFocus = true }
            .onGloballyPositioned {
                initialFocusTargetPlaced.value = true
            }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = !fullScreen),
    ) {
        val windowInfo = LocalWindowInfo.current
        Surface(
            modifier =
                (if (fullScreen) Modifier.fillMaxSize() else Modifier)
                    .semantics { isTraversalGroup = true },
        ) {
            content(initialFocusModifier)
        }
        LaunchedEffect(windowInfo) {
            snapshotFlow {
                initialFocusTargetPlaced.value && windowInfo.isWindowFocused
            }.first { it }
            firstFocus.requestFocus()
        }
    }
    DisposableEffect(returnFocusRequester) {
        onDispose { returnFocusRequester?.let { runCatching { it.requestFocus() } } }
    }
}
