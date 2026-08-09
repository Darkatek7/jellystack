package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class TvNavigationRailState(
    initiallyVisible: Boolean,
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
