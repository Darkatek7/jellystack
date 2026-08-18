package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Owns route-local content focus and the expanded navigation rail as one deterministic state machine. */
internal class TvFocusCoordinator<T : Any>(
    initiallyRailVisible: Boolean = false,
    private val maxRestoreAttempts: Int = 2,
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

    private val routes = mutableMapOf<Any, RouteTargets<T>>()

    var isRailVisible by mutableStateOf(initiallyRailVisible)
        private set

    var registrationRevision by mutableStateOf(0L)
        private set

    init {
        require(maxRestoreAttempts > 0)
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

    suspend fun restoreContentFocus(
        routeKey: Any,
        awaitFrame: suspend () -> Unit,
        requestFocus: (T) -> Boolean,
    ): T? {
        repeat(maxRestoreAttempts) {
            awaitFrame()
            val candidates = focusCandidates(routeKey)
            candidates.forEach { (targetId, target) ->
                if (requestFocus(target)) {
                    rememberFocused(routeKey, targetId, target)
                    return target
                }
            }
        }
        return null
    }

    private fun focusCandidates(routeKey: Any): List<Pair<Any, T>> {
        val route = routes[routeKey] ?: return emptyList()
        val remembered = route.rememberedId?.takeIf { it in route.attached }
        val candidateIds =
            buildList {
                remembered?.let(::add)
                addAll(
                    route.attached
                        .filterValues { registration ->
                            registration.targets.values.any { it.fallback }
                        }.keys,
                )
                addAll(route.attached.keys)
            }.distinct()
        return candidateIds.flatMap { targetId ->
            route.attached.getValue(targetId).targets.keys.map { target -> targetId to target }
        }
    }
}
