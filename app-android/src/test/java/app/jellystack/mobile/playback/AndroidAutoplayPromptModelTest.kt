package app.jellystack.mobile.playback

import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidAutoplayPromptModelTest {
    @Test
    fun autoplayPromptIsDerivedOnlyFromSharedContinuationState() {
        val target = PlaybackContinuationTarget("episode-2", "Next") {}

        assertEquals(
            AndroidAutoplayPromptModel(title = "Next", secondsRemaining = 7),
            androidAutoplayPromptModel(
                PlaybackContinuationState(nextTarget = target, countdownSecondsRemaining = 7),
            ),
        )
        assertNull(androidAutoplayPromptModel(PlaybackContinuationState(nextTarget = target)))
        assertNull(androidAutoplayPromptModel(PlaybackContinuationState(countdownSecondsRemaining = 7)))
    }
}
