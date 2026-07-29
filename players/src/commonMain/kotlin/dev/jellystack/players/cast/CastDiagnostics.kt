package dev.jellystack.players.cast

data class CastLoadRequestSummary(
    val mediaId: String,
    val streamUrl: String,
    val contentType: String?,
    val streamType: CastStreamType,
    val subtitleCount: Int,
    val positionMs: Long,
    val autoplay: Boolean,
)

data class CastDiagnostics(
    val deviceCount: Int = 0,
    val sessionState: CastConnectionState = CastConnectionState.Idle,
    val lastError: String? = null,
    val lastLoadRequest: CastLoadRequestSummary? = null,
    val activeScan: Boolean = false,
)

interface CastDiagnosticsSink {
    fun onSessionState(state: CastConnectionState)

    fun onLastError(message: String?)

    fun onLoadRequest(summary: CastLoadRequestSummary)

    fun onDeviceCount(count: Int)

    fun onActiveScanChanged(enabled: Boolean)
}
