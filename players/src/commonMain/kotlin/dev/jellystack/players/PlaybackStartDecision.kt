package dev.jellystack.players

import dev.jellystack.core.preferences.ResumeMode

/** What a play action should do before playback starts. */
sealed interface PlaybackStartDecision {
    /** Start right away with [policy]. */
    data class Start(
        val policy: PlaybackStartPolicy,
    ) : PlaybackStartDecision

    /** Ask whether to resume at [resumePositionMs] or restart. */
    data class Ask(
        val resumePositionMs: Long,
    ) : PlaybackStartDecision
}

/**
 * The single resume rule for every play entry point: only a saved position combined with
 * [ResumeMode.ASK] needs a prompt. Resume and restart preferences are applied by the controller.
 */
fun decidePlaybackStart(
    resumePositionTicks: Long?,
    resumeMode: ResumeMode,
): PlaybackStartDecision {
    val positionTicks = resumePositionTicks?.takeIf { it > 0L }
    return if (positionTicks != null && resumeMode == ResumeMode.ASK) {
        PlaybackStartDecision.Ask(resumePositionMs = positionTicks.toMillisFromTicks())
    } else {
        PlaybackStartDecision.Start(PlaybackStartPolicy.INHERIT)
    }
}
