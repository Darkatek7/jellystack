package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface TvFocusRestoration<out T> {
    data class Focused<T>(
        val target: T,
    ) : TvFocusRestoration<T>

    data object Failed : TvFocusRestoration<Nothing>
}

/** Owns route-local content focus and the expanded navigation rail as one deterministic state machine. */
internal class TvFocusCoordinator<T : Any>(
    initiallyRailVisible: Boolean = false,
    private val attachmentTimeoutMillis: Long = 1_000,
) {
    private data class TargetRegistration(
        var count: Int,
        var fallback: Boolean,
    )

    private data class Registration<T>(
        val targets: LinkedHashMap<T, TargetRegistration> = linkedMapOf(),
    )

    private data class RouteTargets<T>(
        val attached: LinkedHashMap<Any, Registration<T>> = linkedMapOf(),
        var rememberedId: Any? = null,
    )

    private data class Materializer(
        val targetIds: Set<String>,
        val fallbackTargetIds: Set<String>,
        val materialize: suspend (String) -> Boolean,
    )

    private val routes = mutableMapOf<Any, RouteTargets<T>>()
    private val materializers = mutableMapOf<Any, LinkedHashMap<String, Materializer>>()
    private val restorationLocks = mutableMapOf<Any, Mutex>()

    var isRailVisible by mutableStateOf(initiallyRailVisible)
        private set

    var registrationRevision by mutableStateOf(0L)
        private set

    init {
        require(attachmentTimeoutMillis > 0)
    }

    fun register(
        routeKey: Any,
        target: T,
        fallback: Boolean = false,
    ) = register(routeKey, targetId = target, target = target, fallback = fallback)

    fun register(
        routeKey: Any,
        targetId: Any,
        target: T,
        fallback: Boolean = false,
    ) {
        val route = routes.getOrPut(routeKey) { RouteTargets() }
        val registration = route.attached.getOrPut(targetId) { Registration() }
        val targetRegistration = registration.targets[target]
        if (targetRegistration == null) {
            registration.targets[target] = TargetRegistration(count = 1, fallback = fallback)
            registrationRevision += 1
        } else {
            targetRegistration.count += 1
            targetRegistration.fallback = targetRegistration.fallback || fallback
        }
    }

    fun unregister(
        routeKey: Any,
        target: T,
    ) = unregister(routeKey, targetId = target, target = target)

    fun unregister(
        routeKey: Any,
        targetId: Any,
        target: T,
    ) {
        val route = routes[routeKey] ?: return
        val registration = route.attached[targetId] ?: return
        val targetRegistration = registration.targets[target] ?: return
        targetRegistration.count -= 1
        if (targetRegistration.count <= 0) {
            registration.targets.remove(target)
            registrationRevision += 1
        }
        if (registration.targets.isEmpty()) route.attached.remove(targetId)
    }

    fun rememberFocused(
        routeKey: Any,
        target: T,
    ) = rememberFocused(routeKey, targetId = target, target = target)

    fun rememberFocused(
        routeKey: Any,
        targetId: Any,
        target: T,
    ) {
        val route = routes[routeKey] ?: return
        if (target in route.attached[targetId]?.targets.orEmpty()) route.rememberedId = targetId
    }

    fun registerMaterializer(
        routeKey: Any,
        ownerId: String,
        targetIds: Set<String>,
        fallbackTargetIds: Set<String> = emptySet(),
        materialize: suspend (String) -> Boolean,
    ) {
        materializers.getOrPut(routeKey) { linkedMapOf() }[ownerId] =
            Materializer(targetIds, fallbackTargetIds, materialize)
        registrationRevision += 1
    }

    fun unregisterMaterializer(
        routeKey: Any,
        ownerId: String,
    ) {
        val routeMaterializers = materializers[routeKey] ?: return
        routeMaterializers.remove(ownerId)
        registrationRevision += 1
        if (routeMaterializers.isEmpty()) materializers.remove(routeKey)
    }

    fun needsContentRestoration(routeKey: Any): Boolean {
        val route = routes[routeKey] ?: return true
        return route.rememberedId?.let { route.attached[it]?.targets?.isNotEmpty() } != true
    }

    fun openRail(repeatCount: Int = 0): Boolean {
        if (repeatCount != 0 || isRailVisible) return false
        isRailVisible = true
        return true
    }

    fun closeRail(): Boolean {
        if (!isRailVisible) return false
        isRailVisible = false
        return true
    }

    suspend fun restoreFocus(
        routeKey: Any,
        preferredTargetId: String? = null,
        includeFallback: Boolean = true,
        requestFocus: (T) -> Boolean,
    ): TvFocusRestoration<T> =
        restorationLocks.getOrPut(routeKey) { Mutex() }.withLock {
            awaitRouteCapability(routeKey)
            val rememberedTargetId = preferredTargetId ?: routes[routeKey]?.rememberedId as? String
            if (rememberedTargetId != null) {
                materializeIfNeeded(routeKey, rememberedTargetId)
                focusTarget(routeKey, rememberedTargetId, requestFocus)?.let {
                    return TvFocusRestoration.Focused(it)
                }
            }
            if (!includeFallback) return TvFocusRestoration.Failed
            val fallbackIds =
                buildList {
                    materializers[routeKey]
                        ?.values
                        ?.forEach { addAll(it.fallbackTargetIds) }
                    routes[routeKey]
                        ?.attached
                        ?.filterValues { registration -> registration.targets.values.any { it.fallback } }
                        ?.keys
                        ?.filterIsInstance<String>()
                        ?.let(::addAll)
                }.distinct()
            fallbackIds.forEach { targetId ->
                materializeIfNeeded(routeKey, targetId)
                focusTarget(routeKey, targetId, requestFocus)?.let {
                    return TvFocusRestoration.Focused(it)
                }
            }
            return TvFocusRestoration.Failed
        }

    private suspend fun materializeIfNeeded(
        routeKey: Any,
        targetId: String,
    ): Boolean {
        if (routes[routeKey]
                ?.attached
                ?.get(targetId)
                ?.targets
                ?.isNotEmpty() == true
        ) {
            return true
        }
        var materializer = materializers[routeKey]?.values?.firstOrNull { targetId in it.targetIds }
        if (materializer == null) {
            withTimeoutOrNull(attachmentTimeoutMillis) {
                snapshotFlow { registrationRevision }
                    .first {
                        routes[routeKey]
                            ?.attached
                            ?.get(targetId)
                            ?.targets
                            ?.isNotEmpty() == true ||
                            materializers[routeKey]?.values?.any { targetId in it.targetIds } == true
                    }
            }
            if (routes[routeKey]
                    ?.attached
                    ?.get(targetId)
                    ?.targets
                    ?.isNotEmpty() == true
            ) {
                return true
            }
            materializer = materializers[routeKey]?.values?.firstOrNull { targetId in it.targetIds }
        }
        materializer ?: return false
        if (!materializer.materialize(targetId)) return false
        return awaitTargetAttachment(routeKey, targetId)
    }

    private suspend fun awaitRouteCapability(routeKey: Any) {
        if (routes[routeKey]?.attached?.isNotEmpty() == true || materializers[routeKey]?.isNotEmpty() == true) return
        withTimeoutOrNull(attachmentTimeoutMillis) {
            snapshotFlow { registrationRevision }
                .first {
                    routes[routeKey]?.attached?.isNotEmpty() == true || materializers[routeKey]?.isNotEmpty() == true
                }
        }
    }

    private suspend fun awaitTargetAttachment(
        routeKey: Any,
        targetId: String,
    ): Boolean =
        withTimeoutOrNull(attachmentTimeoutMillis) {
            snapshotFlow { registrationRevision }
                .first {
                    routes[routeKey]
                        ?.attached
                        ?.get(targetId)
                        ?.targets
                        ?.isNotEmpty() == true
                }
            true
        } ?: false

    private fun focusTarget(
        routeKey: Any,
        targetId: String,
        requestFocus: (T) -> Boolean,
    ): T? {
        val targets =
            routes[routeKey]
                ?.attached
                ?.get(targetId)
                ?.targets
                ?.keys
                .orEmpty()
        targets.forEach { target ->
            if (requestFocus(target)) {
                rememberFocused(routeKey, targetId, target)
                return target
            }
        }
        return null
    }
}
