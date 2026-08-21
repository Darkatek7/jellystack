package dev.jellystack.core.preferences

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPlatformCapabilitiesTest {
    @Test
    fun mediaSegmentSkippingIsSupportedOnlyByPlaybackPlatforms() {
        assertFalse(AppPlatformCapabilities().mediaSegmentSkipping)
        assertTrue(AppPlatformCapabilities.Android.mediaSegmentSkipping)
        assertTrue(AppPlatformCapabilities.Television.mediaSegmentSkipping)
    }
}
