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

    data object Cancelled : TvFocusRestoration<Nothing>
}

/** Coordinates stable route-local focus registration and restoration. */
@Suppress("TooManyFunctions") // one coordinator per route; splitting it would hide the state machine
internal class TvFocusCoordinator<T : Any>(
    private val attachmentTimeoutMillis: Long = 1_000,
    private val focusRequestAttempts: Int = 3,
    private val awaitFocusFrame: suspend () -> Unit = {},
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

    var registrationRevision by mutableStateOf(0L)
        private set

    private var interactionRevision = 0L

    init {
        require(attachmentTimeoutMillis > 0)
        require(focusRequestAttempts > 0)
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
        val registration = routes[routeKey]?.attached?.get(targetId) ?: return
        val targetRegistration = registration.targets[target] ?: return
        targetRegistration.count -= 1
        if (targetRegistration.count <= 0) {
            registration.targets.remove(target)
            registrationRevision += 1
            if (registration.targets.isEmpty()) routes[routeKey]?.attached?.remove(targetId)
        }
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
        if (target in route.attached[targetId]?.targets.orEmpty()) {
            route.rememberedId = targetId
            onUserMovement()
        }
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

    /** Invalidates delayed restoration after an explicit D-pad or rail focus movement. */
    fun onUserMovement() {
        interactionRevision += 1L
    }

    suspend fun restoreFocus(
        routeKey: Any,
        preferredTargetId: String? = null,
        includeFallback: Boolean = true,
        requestFocus: (T) -> Boolean,
    ): TvFocusRestoration<T> =
        restorationLocks.getOrPut(routeKey) { Mutex() }.withLock {
            val restorationRevision = interactionRevision
            awaitRouteCapability(routeKey)
            if (interactionRevision != restorationRevision) return TvFocusRestoration.Cancelled
            val rememberedTargetId = preferredTargetId ?: routes[routeKey]?.rememberedId as? String
            if (rememberedTargetId != null) {
                materializeIfNeeded(routeKey, rememberedTargetId)
                if (interactionRevision != restorationRevision) return TvFocusRestoration.Cancelled
                focusTarget(routeKey, rememberedTargetId, restorationRevision, requestFocus)?.let {
                    return TvFocusRestoration.Focused(it)
                }
                if (interactionRevision != restorationRevision) return TvFocusRestoration.Cancelled
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
                if (interactionRevision != restorationRevision) return TvFocusRestoration.Cancelled
                focusTarget(routeKey, targetId, restorationRevision, requestFocus)?.let {
                    return TvFocusRestoration.Focused(it)
                }
                if (interactionRevision != restorationRevision) return TvFocusRestoration.Cancelled
            }
            return TvFocusRestoration.Failed
        }

    private suspend fun materializeIfNeeded(
        routeKey: Any,
        targetId: String,
    ): Boolean =
        when {
            routes[routeKey]
                ?.attached
                ?.get(targetId)
                ?.targets
                ?.isNotEmpty() == true -> true
            else ->
                materializerAwaitingTvTarget(routeKey, targetId)
                    ?.let { materializer -> materializer.materialize(targetId) }
                    ?.let { materialized -> materialized && awaitTargetAttachment(routeKey, targetId) }
                    ?: false
        }

    /** First materializer covering [targetId], waiting up to the attachment timeout for registration. */
    private suspend fun materializerAwaitingTvTarget(
        routeKey: Any,
        targetId: String,
    ): Materializer? {
        withTimeoutOrNull(attachmentTimeoutMillis) {
            snapshotFlow { registrationRevision }.first {
                routes[routeKey]
                    ?.attached
                    ?.get(targetId)
                    ?.targets
                    ?.isNotEmpty() == true ||
                    materializers[routeKey]?.values?.any { targetId in it.targetIds } == true
            }
        }
        return materializers[routeKey]?.values?.firstOrNull { targetId in it.targetIds }
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

    private suspend fun focusTarget(
        routeKey: Any,
        targetId: String,
        restorationRevision: Long,
        requestFocus: (T) -> Boolean,
    ): T? {
        repeat(focusRequestAttempts) {
            awaitFocusFrame()
            if (interactionRevision != restorationRevision) return null
            val targets =
                routes[routeKey]
                    ?.attached
                    ?.get(targetId)
                    ?.targets
                    ?.keys
                    .orEmpty()
            targets.forEach { target ->
                if (requestFocus(target)) {
                    routes[routeKey]?.rememberedId = targetId
                    return target
                }
            }
        }
        return null
    }
}
