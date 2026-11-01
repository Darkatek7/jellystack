@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Opens the pending link's item once the signed-in app of [serverKey] is composed; a foreign link is dropped. */
@Composable
internal fun TvDeepLinkOpenEffect(
    inbox: TvDeepLinkInbox,
    serverKey: String,
    onOpenItem: (itemId: String) -> Unit,
) {
    val pending by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending, serverKey) {
        if (pending != null) inbox.consume(serverKey)?.let(onOpenItem)
    }
}

/** While no account is signed in (connect or reconnect screen) a link has nothing to open, so it is dropped. */
@Composable
internal fun TvDeepLinkSignedOutEffect(
    inbox: TvDeepLinkInbox,
    signedOut: Boolean,
) {
    val pending by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(signedOut, pending) {
        if (signedOut && pending != null) inbox.drop()
    }
}
