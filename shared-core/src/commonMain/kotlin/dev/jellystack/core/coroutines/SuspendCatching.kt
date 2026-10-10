package dev.jellystack.core.coroutines

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspending work: failures become a [Result], but cancellation is rethrown so
 * structured concurrency keeps working. Use it instead of `runCatching` around suspend calls.
 */
suspend inline fun <T> runSuspendCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
