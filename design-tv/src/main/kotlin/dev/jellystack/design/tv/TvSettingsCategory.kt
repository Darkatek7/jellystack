package dev.jellystack.design.tv

internal enum class TvSettingsCategory(
    val routeKey: String,
) {
    APPEARANCE("appearance"),
    PLAYBACK("playback"),
    AUDIO_SUBTITLES("audio-subtitles"),
    SEGMENT_SKIPPING("segment-skipping"),
    CONNECTIONS("connections"),
    ;

    companion object {
        fun fromRouteSection(section: String?): TvSettingsCategory? =
            section
                ?.takeIf(String::isNotBlank)
                ?.let { routeKey -> entries.firstOrNull { it.routeKey == routeKey } }
    }
}

internal fun tvSettingsRoute(category: TvSettingsCategory): TvRoute.Settings = TvRoute.Settings(category.routeKey)

internal fun tvConnectionsSettingsRoute(): TvRoute.Settings = tvSettingsRoute(TvSettingsCategory.CONNECTIONS)

internal fun tvSettingsControlKeys(category: TvSettingsCategory): List<String> =
    when (category) {
        TvSettingsCategory.APPEARANCE -> listOf("language", "home-sections")
        TvSettingsCategory.PLAYBACK ->
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
            )
        TvSettingsCategory.AUDIO_SUBTITLES ->
            listOf("audio-language", "subtitle-language", "subtitle-mode", "subtitle-size", "subtitle-background")
        TvSettingsCategory.SEGMENT_SKIPPING ->
            listOf("segment-intro", "segment-recap", "segment-outro", "segment-preview", "segment-commercial")
        TvSettingsCategory.CONNECTIONS -> emptyList()
    }

internal fun tvSettingsCategoryTargetId(category: TvSettingsCategory): String = "settings:category:${category.routeKey}"
