package dev.jellystack.design

import dev.jellystack.core.jellyfin.HomeSectionsState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLoadingStateTest {
    @Test
    fun enabledHomeSectionsUseStableSkeletonWhilePluginLayoutLoads() {
        assertTrue(
            shouldShowHomeSectionsSkeleton(
                hasServers = true,
                homeSectionsState = HomeSectionsState.Loading,
            ),
        )
        assertFalse(
            shouldShowHomeSectionsSkeleton(
                hasServers = true,
                homeSectionsState = HomeSectionsState.Unavailable,
            ),
        )
    }
}
