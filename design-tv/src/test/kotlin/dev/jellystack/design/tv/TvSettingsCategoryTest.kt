package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvSettingsCategoryTest {
    @Test
    fun categoriesUseStableCanonicalRouteKeys() {
        assertEquals(
            listOf("appearance", "playback", "audio-subtitles", "segment-skipping", "connections"),
            TvSettingsCategory.entries.map { it.routeKey },
        )
    }

    @Test
    fun knownSectionsRoundTripAndUnknownSectionsFallBackToLanding() {
        TvSettingsCategory.entries.forEach { category ->
            assertEquals(category, TvSettingsCategory.fromRouteSection(category.routeKey))
        }
        assertNull(TvSettingsCategory.fromRouteSection(null))
        assertNull(TvSettingsCategory.fromRouteSection(""))
        assertNull(TvSettingsCategory.fromRouteSection("future-category"))
    }

    @Test
    fun settingsFocusKeysCanonicalizeUnknownSectionsToLanding() {
        assertEquals("settings:root", TvRoute.Settings().focusRouteKey())
        assertEquals("settings:root", TvRoute.Settings("future-category").focusRouteKey())
        assertEquals("settings:playback", tvSettingsRoute(TvSettingsCategory.PLAYBACK).focusRouteKey())
        assertEquals("settings:connections", tvConnectionsSettingsRoute().focusRouteKey())
        assertEquals(tvRailTargetId(TvRoute.Settings()), tvRailTargetId(tvConnectionsSettingsRoute()))
    }

    @Test
    fun categoriesOwnOnlyTheirExpectedControlKeys() {
        assertEquals(
            listOf("language", "home-sections"),
            tvSettingsControlKeys(TvSettingsCategory.APPEARANCE),
        )
        assertEquals(
            listOf(
                "quality",
                "autoplay",
                "resume",
                "seek-back",
                "seek-forward",
                "playback-speed",
                "stats",
                "trailer-previews",
                "trailer-preview-sound",
            ),
            tvSettingsControlKeys(TvSettingsCategory.PLAYBACK),
        )
        assertEquals(
            listOf("audio-language", "subtitle-language", "subtitle-mode", "subtitle-size", "subtitle-background"),
            tvSettingsControlKeys(TvSettingsCategory.AUDIO_SUBTITLES),
        )
        assertEquals(
            listOf("segment-intro", "segment-recap", "segment-outro", "segment-preview", "segment-commercial"),
            tvSettingsControlKeys(TvSettingsCategory.SEGMENT_SKIPPING),
        )
        assertEquals(emptyList(), tvSettingsControlKeys(TvSettingsCategory.CONNECTIONS))
    }
}
