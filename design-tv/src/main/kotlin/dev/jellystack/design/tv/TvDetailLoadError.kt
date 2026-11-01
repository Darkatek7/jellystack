@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

internal const val TV_DETAIL_RETRY_TARGET = "detail:retry"

/**
 * A detail page that could not load, with Retry. Retry removes this page while it loads again and brings it back if
 * that fails too, so focus is restored to Retry each time the page appears, as on Library, Discover and Search.
 */
@Composable
internal fun TvDetailLoadError(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvRetryFocusRecovery(remember { TvRetryFocusRequest(revision = 1L, preferredTargetId = TV_DETAIL_RETRY_TARGET) })
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(message, color = TvError)
        TvActionButton(retryLabel, onClick = onRetry, primary = true, focusTargetId = TV_DETAIL_RETRY_TARGET)
    }
}
