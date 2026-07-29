package dev.jellystack.design.cast

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.jellystack.players.cast.CastConnectionState
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.cast_to_device
import org.jetbrains.compose.resources.stringResource

@Suppress("FunctionName")
@Composable
actual fun CastRoutePickerButton(
    state: CastConnectionState,
    modifier: Modifier,
) {
    val castDescription = stringResource(Res.string.cast_to_device)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                CastButtonFactory.setUpMediaRouteButton(context, this)
                contentDescription = castDescription
            }
        },
        update = { button ->
            button.isEnabled = state !is CastConnectionState.Connecting
        },
    )
}
