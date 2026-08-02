package dev.jellystack.core.preferences

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository(
    private val storage: Settings,
) {
    private val mutableSettings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun setAppLanguage(value: AppLanguage) = update(KEY_APP_LANGUAGE, value.name) { copy(appLanguage = value) }

    fun setWifiStreamingQuality(value: StreamingQualityPreference) =
        update(KEY_WIFI_QUALITY, value.name) { copy(wifiStreamingQuality = value) }

    fun setMobileStreamingQuality(value: StreamingQualityPreference) =
        update(KEY_MOBILE_QUALITY, value.name) { copy(mobileStreamingQuality = value) }

    fun setAutoplayNextMode(value: AutoplayNextMode) = update(KEY_AUTOPLAY_NEXT, value.name) { copy(autoplayNextMode = value) }

    fun setResumeMode(value: ResumeMode) = update(KEY_RESUME_MODE, value.name) { copy(resumeMode = value) }

    fun setSeekBackSeconds(value: Int) {
        val normalized = value.takeIf(SEEK_SECONDS::contains) ?: 10
        storage.putInt(KEY_SEEK_BACK, normalized)
        publish { copy(seekBackSeconds = normalized) }
    }

    fun setSeekForwardSeconds(value: Int) {
        val normalized = value.takeIf(SEEK_SECONDS::contains) ?: 30
        storage.putInt(KEY_SEEK_FORWARD, normalized)
        publish { copy(seekForwardSeconds = normalized) }
    }

    fun setPreferredAudioLanguage(value: String?) = updateLanguage(KEY_AUDIO_LANGUAGE, value) { copy(preferredAudioLanguage = it) }

    fun setPreferredSubtitleLanguage(value: String?) = updateLanguage(KEY_SUBTITLE_LANGUAGE, value) { copy(preferredSubtitleLanguage = it) }

    fun setSubtitleMode(value: SubtitleMode) = update(KEY_SUBTITLE_MODE, value.name) { copy(subtitleMode = value) }

    fun setRememberSeriesTracks(value: Boolean) = update(KEY_REMEMBER_SERIES_TRACKS, value) { copy(rememberSeriesTracks = value) }

    fun setSubtitleTextSize(value: SubtitleTextSize) = update(KEY_SUBTITLE_TEXT_SIZE, value.name) { copy(subtitleTextSize = value) }

    fun setSubtitleBackground(value: SubtitleBackground) = update(KEY_SUBTITLE_BACKGROUND, value.name) { copy(subtitleBackground = value) }

    fun setSpotlightAutoCycle(value: Boolean) = update(KEY_SPOTLIGHT_AUTO_CYCLE, value) { copy(spotlightAutoCycle = value) }

    fun setUseServerHomeSections(value: Boolean) = update(KEY_USE_SERVER_HOME_SECTIONS, value) { copy(useServerHomeSections = value) }

    fun setSpotlightIntervalSeconds(value: Int) {
        val normalized = value.takeIf(SPOTLIGHT_INTERVAL_SECONDS::contains) ?: 6
        storage.putInt(KEY_SPOTLIGHT_INTERVAL, normalized)
        publish { copy(spotlightIntervalSeconds = normalized) }
    }

    fun setDownloadsWifiOnly(value: Boolean) = update(KEY_DOWNLOADS_WIFI_ONLY, value) { copy(downloadsWifiOnly = value) }

    fun setDefaultPlaybackSpeed(value: Float) {
        val normalized = value.takeIf(PLAYBACK_SPEEDS::contains) ?: 1f
        storage.putFloat(KEY_DEFAULT_PLAYBACK_SPEED, normalized)
        publish { copy(defaultPlaybackSpeed = normalized) }
    }

    fun setStatsForNerdsEnabled(value: Boolean) = update(KEY_STATS_FOR_NERDS, value) { copy(statsForNerdsEnabled = value) }

    private fun readSettings(): AppSettings =
        AppSettings(
            appLanguage = enumValue(KEY_APP_LANGUAGE, AppLanguage.SYSTEM),
            wifiStreamingQuality = enumValue(KEY_WIFI_QUALITY, StreamingQualityPreference.AUTO),
            mobileStreamingQuality = enumValue(KEY_MOBILE_QUALITY, StreamingQualityPreference.AUTO),
            autoplayNextMode = enumValue(KEY_AUTOPLAY_NEXT, AutoplayNextMode.COUNTDOWN),
            resumeMode = enumValue(KEY_RESUME_MODE, ResumeMode.RESUME),
            seekBackSeconds = storage.getInt(KEY_SEEK_BACK, 10).takeIf(SEEK_SECONDS::contains) ?: 10,
            seekForwardSeconds = storage.getInt(KEY_SEEK_FORWARD, 30).takeIf(SEEK_SECONDS::contains) ?: 30,
            preferredAudioLanguage = storage.getStringOrNull(KEY_AUDIO_LANGUAGE).normalizedLanguage(),
            preferredSubtitleLanguage = storage.getStringOrNull(KEY_SUBTITLE_LANGUAGE).normalizedLanguage(),
            subtitleMode = enumValue(KEY_SUBTITLE_MODE, SubtitleMode.SERVER_DEFAULT),
            rememberSeriesTracks = storage.getBoolean(KEY_REMEMBER_SERIES_TRACKS, true),
            subtitleTextSize = enumValue(KEY_SUBTITLE_TEXT_SIZE, SubtitleTextSize.SYSTEM),
            subtitleBackground = enumValue(KEY_SUBTITLE_BACKGROUND, SubtitleBackground.SYSTEM),
            spotlightAutoCycle = storage.getBoolean(KEY_SPOTLIGHT_AUTO_CYCLE, true),
            useServerHomeSections = storage.getBoolean(KEY_USE_SERVER_HOME_SECTIONS, true),
            spotlightIntervalSeconds =
                storage.getInt(KEY_SPOTLIGHT_INTERVAL, 6).takeIf(SPOTLIGHT_INTERVAL_SECONDS::contains) ?: 6,
            downloadsWifiOnly = storage.getBoolean(KEY_DOWNLOADS_WIFI_ONLY, false),
            defaultPlaybackSpeed = storage.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1f).takeIf(PLAYBACK_SPEEDS::contains) ?: 1f,
            statsForNerdsEnabled = storage.getBoolean(KEY_STATS_FOR_NERDS, false),
        )

    private inline fun <reified T : Enum<T>> enumValue(
        key: String,
        default: T,
    ): T = storage.getStringOrNull(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private fun update(
        key: String,
        value: String,
        block: AppSettings.() -> AppSettings,
    ) {
        storage.putString(key, value)
        publish(block)
    }

    private fun update(
        key: String,
        value: Boolean,
        block: AppSettings.() -> AppSettings,
    ) {
        storage.putBoolean(key, value)
        publish(block)
    }

    private fun updateLanguage(
        key: String,
        value: String?,
        block: AppSettings.(String?) -> AppSettings,
    ) {
        val normalized = value.normalizedLanguage()
        if (normalized == null) storage.remove(key) else storage.putString(key, normalized)
        mutableSettings.value = mutableSettings.value.block(normalized)
    }

    private fun publish(block: AppSettings.() -> AppSettings) {
        mutableSettings.value = mutableSettings.value.block()
    }

    private fun String?.normalizedLanguage(): String? = this?.trim()?.lowercase()?.takeIf(String::isNotBlank)

    private companion object {
        const val KEY_APP_LANGUAGE = "settings.app_language"
        const val KEY_WIFI_QUALITY = "settings.wifi_quality"
        const val KEY_MOBILE_QUALITY = "settings.mobile_quality"
        const val KEY_AUTOPLAY_NEXT = "settings.autoplay_next"
        const val KEY_RESUME_MODE = "settings.resume_mode"
        const val KEY_SEEK_BACK = "settings.seek_back_seconds"
        const val KEY_SEEK_FORWARD = "settings.seek_forward_seconds"
        const val KEY_AUDIO_LANGUAGE = "settings.audio_language"
        const val KEY_SUBTITLE_LANGUAGE = "settings.subtitle_language"
        const val KEY_SUBTITLE_MODE = "settings.subtitle_mode"
        const val KEY_REMEMBER_SERIES_TRACKS = "settings.remember_series_tracks"
        const val KEY_SUBTITLE_TEXT_SIZE = "settings.subtitle_text_size"
        const val KEY_SUBTITLE_BACKGROUND = "settings.subtitle_background"
        const val KEY_SPOTLIGHT_AUTO_CYCLE = "settings.spotlight_auto_cycle"
        const val KEY_USE_SERVER_HOME_SECTIONS = "settings.use_server_home_sections"
        const val KEY_SPOTLIGHT_INTERVAL = "settings.spotlight_interval_seconds"
        const val KEY_DOWNLOADS_WIFI_ONLY = "settings.downloads_wifi_only"
        const val KEY_DEFAULT_PLAYBACK_SPEED = "settings.default_playback_speed"
        const val KEY_STATS_FOR_NERDS = "settings.stats_for_nerds"
        val SEEK_SECONDS = setOf(5, 10, 15, 30, 60)
        val SPOTLIGHT_INTERVAL_SECONDS = setOf(6, 8, 10, 15)
        val PLAYBACK_SPEEDS = setOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
