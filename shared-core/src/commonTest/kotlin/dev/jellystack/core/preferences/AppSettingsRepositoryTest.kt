package dev.jellystack.core.preferences

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsRepositoryTest {
    @Test
    fun emptyPreferencesUseSafeDefaults() {
        val value = AppSettingsRepository(InMemorySettings()).settings.value

        assertEquals(AppLanguage.SYSTEM, value.appLanguage)
        assertEquals(StreamingQualityPreference.AUTO, value.wifiStreamingQuality)
        assertEquals(StreamingQualityPreference.AUTO, value.mobileStreamingQuality)
        assertEquals(AutoplayNextMode.COUNTDOWN, value.autoplayNextMode)
        assertEquals(ResumeMode.RESUME, value.resumeMode)
        assertEquals(10, value.seekBackSeconds)
        assertEquals(30, value.seekForwardSeconds)
        assertNull(value.preferredAudioLanguage)
        assertNull(value.preferredSubtitleLanguage)
        assertEquals(SubtitleMode.SERVER_DEFAULT, value.subtitleMode)
        assertTrue(value.rememberSeriesTracks)
        assertEquals(SubtitleTextSize.SYSTEM, value.subtitleTextSize)
        assertEquals(SubtitleBackground.SYSTEM, value.subtitleBackground)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.introSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.recapSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.outroSkipMode)
        assertEquals(SegmentSkipMode.OFF, value.previewSkipMode)
        assertEquals(SegmentSkipMode.OFF, value.commercialSkipMode)
        assertTrue(value.spotlightAutoCycle)
        assertEquals(6, value.spotlightIntervalSeconds)
        assertTrue(value.useServerHomeSections)
        assertTrue(value.trailerPreviewsEnabled)
        assertTrue(value.trailerPreviewSoundEnabled)
        assertFalse(value.downloadsWifiOnly)
        assertEquals(1f, value.defaultPlaybackSpeed)
        assertFalse(value.statsForNerdsEnabled)
    }

    @Test
    fun settersPersistAndPublishOneCoherentSnapshot() {
        val settings = InMemorySettings()
        val repository = AppSettingsRepository(settings)

        repository.setAppLanguage(AppLanguage.GERMAN)
        repository.setWifiStreamingQuality(StreamingQualityPreference.MBPS_8_1080P)
        repository.setMobileStreamingQuality(StreamingQualityPreference.KBPS_720_480P)
        repository.setAutoplayNextMode(AutoplayNextMode.OFF)
        repository.setResumeMode(ResumeMode.ASK)
        repository.setSeekBackSeconds(15)
        repository.setSeekForwardSeconds(60)
        repository.setPreferredAudioLanguage("deu")
        repository.setPreferredSubtitleLanguage("eng")
        repository.setSubtitleMode(SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS)
        repository.setRememberSeriesTracks(false)
        repository.setSubtitleTextSize(SubtitleTextSize.LARGE)
        repository.setSubtitleBackground(SubtitleBackground.DARK)
        repository.setIntroSkipMode(SegmentSkipMode.AUTO_SKIP)
        repository.setRecapSkipMode(SegmentSkipMode.OFF)
        repository.setOutroSkipMode(SegmentSkipMode.AUTO_SKIP)
        repository.setPreviewSkipMode(SegmentSkipMode.SHOW_BUTTON)
        repository.setCommercialSkipMode(SegmentSkipMode.SHOW_BUTTON)
        repository.setSpotlightAutoCycle(false)
        repository.setSpotlightIntervalSeconds(15)
        repository.setUseServerHomeSections(false)
        repository.setTrailerPreviewsEnabled(false)
        repository.setTrailerPreviewSoundEnabled(false)
        repository.setDownloadsWifiOnly(true)
        repository.setDefaultPlaybackSpeed(1.5f)
        repository.setStatsForNerdsEnabled(true)

        assertEquals(
            repository.settings.value,
            AppSettingsRepository(settings).settings.value,
        )
        assertEquals(AppLanguage.GERMAN, repository.settings.value.appLanguage)
        assertEquals("deu", repository.settings.value.preferredAudioLanguage)
        assertEquals(SegmentSkipMode.AUTO_SKIP, repository.settings.value.introSkipMode)
        assertEquals(SegmentSkipMode.OFF, repository.settings.value.recapSkipMode)
        assertEquals(SegmentSkipMode.AUTO_SKIP, repository.settings.value.outroSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, repository.settings.value.previewSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, repository.settings.value.commercialSkipMode)
        assertTrue(repository.settings.value.downloadsWifiOnly)
        assertFalse(repository.settings.value.useServerHomeSections)
        assertFalse(repository.settings.value.trailerPreviewsEnabled)
        assertFalse(repository.settings.value.trailerPreviewSoundEnabled)
        assertEquals(1.5f, repository.settings.value.defaultPlaybackSpeed)
        assertTrue(repository.settings.value.statsForNerdsEnabled)
    }

    @Test
    fun malformedValuesFallBackAndNumericValuesAreConstrained() {
        val settings =
            InMemorySettings(
                mapOf(
                    "settings.app_language" to "KLINGON",
                    "settings.mobile_quality" to "BROKEN",
                    "settings.seek_back_seconds" to 999,
                    "settings.spotlight_interval_seconds" to 7,
                    "settings.audio_language" to "  ",
                    "settings.default_playback_speed" to 3f,
                    "settings.intro_skip_mode" to "BROKEN",
                    "settings.recap_skip_mode" to "AUTO_SKIP",
                    "settings.outro_skip_mode" to "BROKEN",
                    "settings.preview_skip_mode" to "SHOW_BUTTON",
                    "settings.commercial_skip_mode" to "BROKEN",
                ),
            )

        val value = AppSettingsRepository(settings).settings.value

        assertEquals(AppLanguage.SYSTEM, value.appLanguage)
        assertEquals(StreamingQualityPreference.AUTO, value.mobileStreamingQuality)
        assertEquals(10, value.seekBackSeconds)
        assertEquals(6, value.spotlightIntervalSeconds)
        assertNull(value.preferredAudioLanguage)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.introSkipMode)
        assertEquals(SegmentSkipMode.AUTO_SKIP, value.recapSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.outroSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, value.previewSkipMode)
        assertEquals(SegmentSkipMode.OFF, value.commercialSkipMode)
        assertEquals(1f, value.defaultPlaybackSpeed)
    }

    @Test
    fun segmentSettersChangeOnlyTheirOwnModes() {
        val repository = AppSettingsRepository(InMemorySettings())

        repository.setIntroSkipMode(SegmentSkipMode.AUTO_SKIP)

        assertEquals(SegmentSkipMode.AUTO_SKIP, repository.settings.value.introSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, repository.settings.value.recapSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, repository.settings.value.outroSkipMode)
        assertEquals(SegmentSkipMode.OFF, repository.settings.value.previewSkipMode)
        assertEquals(SegmentSkipMode.OFF, repository.settings.value.commercialSkipMode)
    }

    @Test
    fun televisionCapabilitiesExcludeMobileOnlyFeatures() {
        val capabilities = AppPlatformCapabilities.Television

        assertTrue(capabilities.isTelevision)
        assertTrue(capabilities.appLanguageSelection)
        assertTrue(capabilities.autoplayNextEpisode)
        assertTrue(capabilities.subtitleAppearance)
        assertFalse(capabilities.supportsCast)
        assertFalse(capabilities.supportsDownloads)
        assertFalse(capabilities.supportsBiometricLock)
        assertFalse(capabilities.supportsAdmin)
    }
}
