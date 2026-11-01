package dev.jellystack.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO

/**
 * Dispatcher every SQL store runs its queries on, so SQLite never executes on the caller's (usually Main) thread.
 *
 * It is serial because SQLite serialises writers anyway; a single lane avoids lock contention and keeps a
 * transaction on one thread. Provide it once through Koin and pass [value] to each store.
 */
class DatabaseDispatcher
    @OptIn(ExperimentalCoroutinesApi::class)
    constructor(
        val value: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    )
