package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class TvNavigationRailState(
    initiallyVisible: Boolean = false,
) {
    var isVisible by mutableStateOf(initiallyVisible)
        private set

    fun onDestinationSelected() {
        isVisible = false
    }

    fun onContentLeftEdge() {
        isVisible = true
    }
}

internal class TvContentFocusMemory<T> {
    private var routeKey: Any? = null
    private var target: T? = null

    fun remember(routeKey: Any, target: T) {
        this.routeKey = routeKey
        this.target = target
    }

    fun restore(routeKey: Any): T? = target.takeIf { this.routeKey == routeKey }
}
