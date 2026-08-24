package dev.jellystack.core.profile

import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfilePreferencesRepositoryTest {
    @Test
    fun profileValuesAreIsolatedWhileDeviceValuesRemainShared() {
        val storage = InMemorySettings()
        val app = AppSettingsRepository(storage)
        val profiles = ProfilePreferencesRepository(storage)

        app.setAppLanguage(AppLanguage.GERMAN)
        app.setWifiStreamingQuality(StreamingQualityPreference.MBPS_20_2160P)
        profiles.setPreferredAudioLanguage(PROFILE_A, "de")
        profiles.setSubtitleMode(PROFILE_A, SubtitleMode.FORCED_ONLY)
        profiles.setAutoplayNextMode(PROFILE_A, AutoplayNextMode.OFF)
        profiles.setResumeMode(PROFILE_A, ResumeMode.ASK)
        profiles.setRememberSeriesTracks(PROFILE_A, false)
        profiles.setDefaultPlaybackSpeed(PROFILE_A, 1.5f)
        profiles.setHomeShelfOrder(PROFILE_A, listOf("my-list", "next-up"))
        profiles.setPreferredAudioLanguage(PROFILE_B, "en")

        assertEquals("de", profiles.preferences(PROFILE_A).value.preferredAudioLanguage)
        assertEquals(SubtitleMode.FORCED_ONLY, profiles.preferences(PROFILE_A).value.subtitleMode)
        assertEquals(AutoplayNextMode.OFF, profiles.preferences(PROFILE_A).value.autoplayNextMode)
        assertEquals(ResumeMode.ASK, profiles.preferences(PROFILE_A).value.resumeMode)
        assertFalse(profiles.preferences(PROFILE_A).value.rememberSeriesTracks)
        assertEquals(1.5f, profiles.preferences(PROFILE_A).value.defaultPlaybackSpeed)
        assertEquals(listOf("my-list", "next-up"), profiles.preferences(PROFILE_A).value.homeShelfOrder)
        assertEquals("en", profiles.preferences(PROFILE_B).value.preferredAudioLanguage)
        assertEquals(SubtitleMode.SERVER_DEFAULT, profiles.preferences(PROFILE_B).value.subtitleMode)
        assertEquals(AppLanguage.GERMAN, app.settings.value.appLanguage)
        assertEquals(StreamingQualityPreference.MBPS_20_2160P, app.settings.value.wifiStreamingQuality)
        assertEquals(
            "de",
            profiles
                .preferences(PROFILE_A)
                .value
                .applyTo(app.settings.value)
                .preferredAudioLanguage,
        )
        assertTrue(storage.keys.all { !it.contains(PROFILE_B) || it.startsWith("profile.$PROFILE_B.") })
    }

    @Test
    fun legacyValuesAreCopiedExactlyOnceIntoDefaultProfile() {
        val storage =
            InMemorySettings(
                mapOf(
                    "settings.audio_language" to "de",
                    "settings.subtitle_language" to "en",
                    "settings.subtitle_mode" to "PREFERRED_ALWAYS",
                    "settings.remember_series_tracks" to false,
                    "settings.autoplay_next" to "IMMEDIATE",
                    "settings.resume_mode" to "RESTART",
                    "settings.default_playback_speed" to 1.25f,
                ),
            )
        val repository = ProfilePreferencesRepository(storage)

        assertTrue(repository.migrateLegacyProfile(PROFILE_A))
        storage.putString("settings.audio_language", "fr")
        assertFalse(repository.migrateLegacyProfile(PROFILE_A))

        assertEquals(
            ProfilePreferences(
                preferredAudioLanguage = "de",
                preferredSubtitleLanguage = "en",
                subtitleMode = SubtitleMode.PREFERRED_ALWAYS,
                rememberSeriesTracks = false,
                autoplayNextMode = AutoplayNextMode.IMMEDIATE,
                resumeMode = ResumeMode.RESTART,
                defaultPlaybackSpeed = 1.25f,
            ),
            repository.preferences(PROFILE_A).value,
        )
    }

    @Test
    fun deletingProfilePreferencesDoesNotChangeDeviceSettingsOrAnotherProfile() {
        val storage = InMemorySettings()
        val app = AppSettingsRepository(storage)
        val repository = ProfilePreferencesRepository(storage)
        app.setAppLanguage(AppLanguage.GERMAN)
        repository.setPreferredAudioLanguage(PROFILE_A, "de")
        repository.setPreferredAudioLanguage(PROFILE_B, "en")

        repository.delete(PROFILE_A)

        assertEquals(ProfilePreferences(), repository.preferences(PROFILE_A).value)
        assertEquals("en", repository.preferences(PROFILE_B).value.preferredAudioLanguage)
        assertEquals(AppLanguage.GERMAN, app.settings.value.appLanguage)
    }

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}
