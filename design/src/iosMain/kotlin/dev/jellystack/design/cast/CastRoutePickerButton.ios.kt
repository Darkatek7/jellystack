package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jellystack.players.cast.CastConnectionState
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.cast_to_device
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.interop.UIKitView
import platform.AVKit.AVRoutePickerView

@Suppress("FunctionName")
@Composable
actual fun CastRoutePickerButton(
    state: CastConnectionState,
    modifier: Modifier,
) {
    val castDescription = stringResource(Res.string.cast_to_device)
    UIKitView(
        modifier = modifier,
        factory = {
            AVRoutePickerView().apply {
                accessibilityLabel = castDescription
            }
        },
        update = { view ->
            view.setEnabled(state !is CastConnectionState.Connecting)
        },
    )
}
