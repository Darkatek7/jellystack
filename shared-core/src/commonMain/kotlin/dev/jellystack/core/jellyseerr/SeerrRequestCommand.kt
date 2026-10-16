package dev.jellystack.core.jellyseerr

enum class SeerrPrimaryAction {
    Request,
    RequestMoreSeasons,
}

data class SeerrRequestCommand(
    val primaryAction: SeerrPrimaryAction?,
    val showStatus: Boolean,
    val permissionDenied: Boolean = false,
)

fun resolveSeerrRequestCommand(
    mediaType: JellyseerrMediaType,
    mediaStatus: JellyseerrMediaStatus?,
    hasRequest: Boolean,
    requestableSeasons: List<Int>,
    capabilities: JellyseerrRequestCapabilities = JellyseerrRequestCapabilities.ALL,
): SeerrRequestCommand {
    val requestMoreSeasons =
        mediaType == JellyseerrMediaType.TV &&
            requestableSeasons.isNotEmpty() &&
            (
                hasRequest ||
                    mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE
            )
    val candidateAction =
        when {
            mediaStatus == JellyseerrMediaStatus.AVAILABLE -> null
            mediaStatus == JellyseerrMediaStatus.PENDING -> null
            mediaStatus == JellyseerrMediaStatus.PROCESSING -> null
            requestMoreSeasons -> SeerrPrimaryAction.RequestMoreSeasons
            mediaStatus == JellyseerrMediaStatus.PARTIALLY_AVAILABLE -> null
            hasRequest -> null
            else -> SeerrPrimaryAction.Request
        }
    val permissionDenied =
        candidateAction != null &&
            !capabilities.canRequest(mediaType, JellyseerrRequestVariant.STANDARD) &&
            !capabilities.canRequest(mediaType, JellyseerrRequestVariant.FOUR_K)
    val primaryAction = candidateAction.takeUnless { permissionDenied }
    return SeerrRequestCommand(
        primaryAction = primaryAction,
        showStatus =
            permissionDenied ||
                hasRequest ||
                mediaStatus != null &&
                mediaStatus != JellyseerrMediaStatus.UNKNOWN,
        permissionDenied = permissionDenied,
    )
}
