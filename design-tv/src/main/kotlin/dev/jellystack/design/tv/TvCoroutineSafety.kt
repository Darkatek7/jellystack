package dev.jellystack.design.tv

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last line of defence for fire-and-forget `launch` calls in long-lived TV scopes: a failure nobody caught is
 * logged (which also lands in the local diagnostics buffer) instead of reaching the thread's uncaught-exception
 * handler and killing the app. Call sites should still catch the failures they can explain to the user.
 */
internal fun tvCoroutineFailureHandler(owner: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, failure ->
        if (failure is CancellationException) return@CoroutineExceptionHandler
        JellystackLog.e("Unhandled failure in $owner", failure)
    }
