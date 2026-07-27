package dev.jellystack.core.preferences

enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de"),
}

enum class StreamingQualityPreference(
    val maxBitrate: Int?,
    val maxHeight: Int?,
) {
    AUTO(null, null),
    MBPS_120_2160P(120_000_000, 2160),
    MBPS_80_2160P(80_000_000, 2160),
    MBPS_60_2160P(60_000_000, 2160),
    MBPS_40_2160P(40_000_000, 2160),
    MBPS_20_2160P(20_000_000, 2160),
    MBPS_15_1440P(15_000_000, 1440),
    MBPS_10_1440P(10_000_000, 1440),
    MBPS_8_1080P(8_000_000, 1080),
    MBPS_6_1080P(6_000_000, 1080),
    MBPS_4_720P(4_000_000, 720),
    MBPS_3_720P(3_000_000, 720),
    MBPS_1_5_720P(1_500_000, 720),
    KBPS_720_480P(720_000, 480),
    KBPS_420_360P(420_000, 360),
}

enum class AutoplayNextMode {
    OFF,
    COUNTDOWN,
    IMMEDIATE,
}

enum class ResumeMode {
    RESUME,
    ASK,
    RESTART,
}

enum class SubtitleMode {
    SERVER_DEFAULT,
    OFF,
    FORCED_ONLY,
    PREFERRED_ALWAYS,
    PREFERRED_WHEN_AUDIO_DIFFERS,
}

enum class SubtitleTextSize {
    SYSTEM,
    SMALL,
    MEDIUM,
    LARGE,
}

enum class SubtitleBackground {
    SYSTEM,
    NONE,
    TRANSLUCENT,
    DARK,
}

data class AppSettings(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val wifiStreamingQuality: StreamingQualityPreference = StreamingQualityPreference.AUTO,
    val mobileStreamingQuality: StreamingQualityPreference = StreamingQualityPreference.AUTO,
    val autoplayNextMode: AutoplayNextMode = AutoplayNextMode.COUNTDOWN,
    val resumeMode: ResumeMode = ResumeMode.RESUME,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.SERVER_DEFAULT,
    val rememberSeriesTracks: Boolean = true,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.SYSTEM,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.SYSTEM,
    val spotlightAutoCycle: Boolean = true,
    val spotlightIntervalSeconds: Int = 6,
    val downloadsWifiOnly: Boolean = false,
)

data class AppPlatformCapabilities(
    val appLanguageSelection: Boolean = false,
    val autoplayNextEpisode: Boolean = false,
    val meteredDownloadPolicy: Boolean = false,
    val subtitleAppearance: Boolean = false,
) {
    companion object {
        val Android =
            AppPlatformCapabilities(
                appLanguageSelection = true,
                autoplayNextEpisode = true,
                meteredDownloadPolicy = true,
                subtitleAppearance = true,
            )
    }
}
