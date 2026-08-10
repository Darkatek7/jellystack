package dev.jellystack.design.tv

internal data class TvConnectionContentMode(
    val showEditableFields: Boolean,
    val showConnectAction: Boolean,
    val showWaitingInstructions: Boolean,
)

internal fun connectionContentMode(quickConnectInProgress: Boolean): TvConnectionContentMode =
    TvConnectionContentMode(
        showEditableFields = !quickConnectInProgress,
        showConnectAction = !quickConnectInProgress,
        showWaitingInstructions = quickConnectInProgress,
    )
