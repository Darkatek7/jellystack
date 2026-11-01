package app.jellystack.tv.watchnext

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchNextSupportTest {
    private fun supported(
        sdkInt: Int = 34,
        isFireTv: Boolean = false,
        isLeanback: Boolean = true,
        providerAvailable: Boolean = true,
    ) = WatchNextSupport.isSupported(sdkInt, isFireTv, isLeanback, providerAvailable)

    @Test
    fun googleTvAndAndroidTvQualify() {
        assertTrue(supported())
        assertTrue(supported(sdkInt = 26))
    }

    @Test
    fun fireTvDoesNothingEvenWithAProvider() {
        assertFalse(supported(isFireTv = true))
    }

    @Test
    fun missingProviderOldAndroidOrNonTelevisionDoNothing() {
        assertFalse(supported(providerAvailable = false))
        assertFalse(supported(sdkInt = 25))
        assertFalse(supported(isLeanback = false))
    }
}
