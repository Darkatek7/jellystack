package dev.jellystack.design.tv

/** Labels of the audio and subtitle delay panel. */
internal data class TvPlayerSyncStrings(
    val adjustment: String,
    val adjustmentNone: String,
    val audioDelay: String,
    val subtitleDelay: String,
    val subtitleDelayNeedsSubtitle: String,
    val resetDelays: String,
) {
    companion object {
        val ENGLISH =
            TvPlayerSyncStrings(
                adjustment = "Audio and subtitle sync",
                adjustmentNone = "No adjustment",
                audioDelay = "Audio delay",
                subtitleDelay = "Subtitle delay",
                subtitleDelayNeedsSubtitle = "Turn on a subtitle first",
                resetDelays = "Reset both",
            )

        val GERMAN =
            TvPlayerSyncStrings(
                adjustment = "Ton- und Untertitel-Sync",
                adjustmentNone = "Keine Anpassung",
                audioDelay = "Tonverzögerung",
                subtitleDelay = "Untertitelverzögerung",
                subtitleDelayNeedsSubtitle = "Zuerst einen Untertitel einschalten",
                resetDelays = "Beide zurücksetzen",
            )
    }
}
