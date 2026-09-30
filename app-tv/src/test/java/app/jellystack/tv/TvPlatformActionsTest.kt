package app.jellystack.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlatformActionsTest {
    @Test
    fun playbackTransitionsOwnWakeLockAndSessionStartExactlyOnce() {
        val actions = RecordingPlatformActions()
        val coordinator = TvPlatformActionCoordinator(actions)

        coordinator.onPlaybackActivityChanged(active = false)
        coordinator.onPlaybackActivityChanged(active = true)
        coordinator.onPlaybackActivityChanged(active = true)
        coordinator.onPlaybackActivityChanged(active = false)

        assertEquals(listOf(false, true, true, false), actions.keepScreenOn)
        assertEquals(1, actions.playbackStarts)
    }

    @Test
    fun mediaKeysAreOnlyOfferedToVisiblePlayback() {
        val actions = RecordingPlatformActions(mediaKeyHandled = true)
        val coordinator = TvPlatformActionCoordinator(actions)

        assertFalse(coordinator.dispatchMediaKey("play", playbackVisible = false))
        assertTrue(coordinator.dispatchMediaKey("play", playbackVisible = true))
        assertEquals(listOf("play"), actions.mediaKeys)
    }

    @Test
    fun stopAlwaysDisarmsTrailersAndPreservesPlaybackAcrossRecreationOnly() {
        val actions = RecordingPlatformActions()
        val coordinator = TvPlatformActionCoordinator(actions)

        coordinator.onStop(isChangingConfigurations = true)
        coordinator.onStop(isChangingConfigurations = false)

        assertEquals(2, actions.trailerStops)
        assertEquals(1, actions.playbackStops)
    }

    @Test
    fun destroyReleasesEveryPlatformResourceInDependencyOrder() {
        val actions = RecordingPlatformActions()

        TvPlatformActionCoordinator(actions).onDestroy()

        assertEquals(listOf("trailer", "bridge", "playback"), actions.releases)
    }

    private class RecordingPlatformActions(
        private val mediaKeyHandled: Boolean = false,
    ) : TvPlatformActions<String> {
        val keepScreenOn = mutableListOf<Boolean>()
        var playbackStarts = 0
        val mediaKeys = mutableListOf<String>()
        var trailerStops = 0
        var playbackStops = 0
        val releases = mutableListOf<String>()

        override fun setKeepScreenOn(enabled: Boolean) {
            keepScreenOn += enabled
        }

        override fun markPlaybackStarted() {
            playbackStarts += 1
        }

        override fun handleMediaKey(event: String): Boolean {
            mediaKeys += event
            return mediaKeyHandled
        }

        override fun stopTrailer() {
            trailerStops += 1
        }

        override fun stopPlayback() {
            playbackStops += 1
        }

        override fun releaseTrailer() {
            releases += "trailer"
        }

        override fun releaseBridge() {
            releases += "bridge"
        }

        override fun releasePlayback() {
            releases += "playback"
        }
    }
}
