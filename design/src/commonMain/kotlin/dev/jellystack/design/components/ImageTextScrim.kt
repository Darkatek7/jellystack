package dev.jellystack.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics

@Suppress("FunctionName")
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageTextScrim(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.semantics { invisibleToUser() }.background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.38f),
                    1f to Color.Black.copy(alpha = 0.88f),
                ),
            ),
    )
}
