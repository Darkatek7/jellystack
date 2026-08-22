package dev.jellystack.design.tv

internal fun tvSubtitleBottomPaddingFraction(
    controlsVisible: Boolean,
    standaloneActionsVisible: Boolean,
    panelOpen: Boolean,
): Float =
    when {
        panelOpen || controlsVisible -> TV_SUBTITLE_CONTROLS_PADDING_FRACTION
        standaloneActionsVisible -> TV_SUBTITLE_STANDALONE_ACTION_PADDING_FRACTION
        else -> TV_SUBTITLE_NORMAL_PADDING_FRACTION
    }

internal const val TV_SUBTITLE_NORMAL_PADDING_FRACTION = 0.08f
private const val TV_SUBTITLE_STANDALONE_ACTION_PADDING_FRACTION = 0.20f
private const val TV_SUBTITLE_CONTROLS_PADDING_FRACTION = 0.38f
