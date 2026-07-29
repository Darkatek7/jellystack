package app.jellystack.mobile.cast

import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastDiagnostics
import dev.jellystack.players.cast.CastDiagnosticsSink
import dev.jellystack.players.cast.CastLoadRequestSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidCastDiagnosticsStore : CastDiagnosticsSink {
    private val _state = MutableStateFlow(CastDiagnostics())
    val state: StateFlow<CastDiagnostics> = _state

    override fun onSessionState(state: CastConnectionState) {
        _state.value = _state.value.copy(sessionState = state)
    }

    override fun onLastError(message: String?) {
        _state.value = _state.value.copy(lastError = message)
    }

    override fun onLoadRequest(summary: CastLoadRequestSummary) {
        _state.value = _state.value.copy(lastLoadRequest = summary)
    }

    override fun onDeviceCount(count: Int) {
        _state.value = _state.value.copy(deviceCount = count)
    }

    override fun onActiveScanChanged(enabled: Boolean) {
        _state.value = _state.value.copy(activeScan = enabled)
    }
}
