package dev.jellystack.players

import dev.jellystack.core.preferences.ResumeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackStartDecisionTest {
    @Test
    fun askModeWithSavedPositionAsksWithThePositionInMilliseconds() {
        assertEquals(
            PlaybackStartDecision.Ask(resumePositionMs = 754_000L),
            decidePlaybackStart(resumePositionTicks = 7_540_000_000L, resumeMode = ResumeMode.ASK),
        )
    }

    @Test
    fun withoutSavedPositionPlaybackStartsEvenInAskMode() {
        val start = PlaybackStartDecision.Start(PlaybackStartPolicy.INHERIT)

        assertEquals(start, decidePlaybackStart(resumePositionTicks = null, resumeMode = ResumeMode.ASK))
        assertEquals(start, decidePlaybackStart(resumePositionTicks = 0L, resumeMode = ResumeMode.ASK))
    }

    @Test
    fun resumeAndRestartModesNeverAsk() {
        val start = PlaybackStartDecision.Start(PlaybackStartPolicy.INHERIT)

        assertEquals(start, decidePlaybackStart(resumePositionTicks = 7_540_000_000L, resumeMode = ResumeMode.RESUME))
        assertEquals(start, decidePlaybackStart(resumePositionTicks = 7_540_000_000L, resumeMode = ResumeMode.RESTART))
    }
}
