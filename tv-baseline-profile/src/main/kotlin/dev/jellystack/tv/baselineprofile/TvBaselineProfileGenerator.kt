package dev.jellystack.tv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Home, Library, Search, Discover, Settings: Library is one entry below Home.
private const val RAIL_STEPS_TO_LIBRARY = 1
private const val BROWSE_MAX_ITERATIONS = 6
private const val BROWSE_STABLE_ITERATIONS = 2

/**
 * Generates the TV app's baseline profile and startup profile from the production app. The device needs a
 * signed-in profile on a Jellyfin server with a few libraries.
 *
 * The cold start is collected on its own so that only start-up code lands in the startup profile (used for
 * dex layout); the browse journey then covers the code a person touches in the first minutes.
 */
@RunWith(AndroidJUnit4::class)
class TvBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() =
        baselineProfileRule.collect(
            packageName = TV_APP_PACKAGE,
            outputFilePrefix = "startup",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startTvAppAtHome()
        }

    @Test
    fun browse() =
        baselineProfileRule.collect(
            packageName = TV_APP_PACKAGE,
            outputFilePrefix = "browse",
            maxIterations = BROWSE_MAX_ITERATIONS,
            stableIterations = BROWSE_STABLE_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startTvAppAtHome()

            // Detail page of the spotlight item (focus starts on the spotlight).
            device.openSpotlightDetail()
            device.backToHome()

            // Rows and cards.
            device.traverseHomeRows()

            // Library through the navigation rail.
            device.openRailEntry(RAIL_STEPS_TO_LIBRARY)
            device.backToHome()
        }
}
