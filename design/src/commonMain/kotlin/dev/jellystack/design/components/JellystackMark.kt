package dev.jellystack.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics

@Suppress("FunctionName")
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun JellystackMark(modifier: Modifier = Modifier) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.semantics { invisibleToUser() }) {
        drawCircle(color = primaryContainer)
        drawCircle(
            color = primary,
            radius = size.minDimension / 4f,
        )
    }
}
