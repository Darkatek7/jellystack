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
        assertTrue(value.spotlightAutoCycle)
        assertEquals(6, value.spotlightIntervalSeconds)
        assertTrue(value.useServerHomeSections)
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
        repository.setSpotlightAutoCycle(false)
        repository.setSpotlightIntervalSeconds(15)
        repository.setUseServerHomeSections(false)
        repository.setDownloadsWifiOnly(true)
        repository.setDefaultPlaybackSpeed(1.5f)
        repository.setStatsForNerdsEnabled(true)

        assertEquals(
            repository.settings.value,
            AppSettingsRepository(settings).settings.value,
        )
        assertEquals(AppLanguage.GERMAN, repository.settings.value.appLanguage)
        assertEquals("deu", repository.settings.value.preferredAudioLanguage)
        assertTrue(repository.settings.value.downloadsWifiOnly)
        assertFalse(repository.settings.value.useServerHomeSections)
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
                ),
            )

        val value = AppSettingsRepository(settings).settings.value

        assertEquals(AppLanguage.SYSTEM, value.appLanguage)
        assertEquals(StreamingQualityPreference.AUTO, value.mobileStreamingQuality)
        assertEquals(10, value.seekBackSeconds)
        assertEquals(6, value.spotlightIntervalSeconds)
        assertNull(value.preferredAudioLanguage)
        assertEquals(1f, value.defaultPlaybackSpeed)
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
