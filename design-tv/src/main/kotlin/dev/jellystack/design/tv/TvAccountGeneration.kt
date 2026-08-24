package dev.jellystack.design.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Owns every asynchronous operation that may publish authenticated TV content. */
internal class TvAccountGeneration(
    val identity: TvAuthenticatedEnvironmentIdentity,
    parentScope: CoroutineScope,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    val scope = CoroutineScope(parentScope.coroutineContext + job)

    fun close() {
        scope.cancel()
    }
}
