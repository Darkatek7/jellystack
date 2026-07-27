package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jellystack.players.cast.CastConnectionState

@Suppress("FunctionName")
@Composable
expect fun CastRoutePickerButton(
    state: CastConnectionState,
    modifier: Modifier = Modifier,
)
