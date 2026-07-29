package dev.jellystack.players

import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleMode

enum class PlaybackNetworkClass {
    UNMETERED,
    METERED,
    UNKNOWN,
}

fun interface PlaybackNetworkClassifier {
    fun currentNetworkClass(): PlaybackNetworkClass
}

object UnknownPlaybackNetworkClassifier : PlaybackNetworkClassifier {
    override fun currentNetworkClass(): PlaybackNetworkClass = PlaybackNetworkClass.UNKNOWN
}

fun interface PlaybackPreferencesProvider {
    fun currentSettings(): AppSettings
}

object DefaultPlaybackPreferencesProvider : PlaybackPreferencesProvider {
    override fun currentSettings(): AppSettings = AppSettings()
}

class PlaybackPreferenceResolver(
    private val settings: AppSettings,
    networkClass: PlaybackNetworkClass = PlaybackNetworkClass.UNMETERED,
) {
    val streamingQuality: StreamingQualityPreference =
        when (networkClass) {
            PlaybackNetworkClass.UNMETERED -> settings.wifiStreamingQuality
            PlaybackNetworkClass.METERED,
            PlaybackNetworkClass.UNKNOWN,
            -> settings.mobileStreamingQuality
        }

    fun selectQualityOption(options: List<PlaybackQualityOption>): PlaybackQualityOption? {
        if (streamingQuality == StreamingQualityPreference.AUTO) {
            return options.firstOrNull { it.isAuto }
        }
        return options.firstOrNull { option ->
            option.maxBitrate == streamingQuality.maxBitrate && option.maxHeight == streamingQuality.maxHeight
        } ?: options
            .filter { !it.isAuto && it.maxBitrate != null }
            .minByOrNull { option -> kotlin.math.abs(requireNotNull(option.maxBitrate) - requireNotNull(streamingQuality.maxBitrate)) }
    }

    fun selectAudioTrack(tracks: List<AudioTrack>): AudioTrack? =
        settings.preferredAudioLanguage
            ?.let { preferred -> tracks.firstOrNull { it.language.languageMatches(preferred) } }
            ?: tracks.firstOrNull { it.isDefault }
            ?: tracks.firstOrNull()

    fun selectSubtitleTrack(
        tracks: List<SubtitleTrack>,
        audioLanguage: String?,
    ): SubtitleTrack? {
        val preferred = settings.preferredSubtitleLanguage
        val preferredTrack = preferred?.let { language -> tracks.firstOrNull { it.language.languageMatches(language) } }
        val forcedPreferred =
            preferred?.let { language -> tracks.firstOrNull { it.isForced && it.language.languageMatches(language) } }
        val forced = forcedPreferred ?: tracks.firstOrNull { it.isForced }
        return when (settings.subtitleMode) {
            SubtitleMode.SERVER_DEFAULT -> tracks.firstOrNull { it.isDefault } ?: tracks.firstOrNull { !it.isForced }
            SubtitleMode.OFF -> null
            SubtitleMode.FORCED_ONLY -> forced
            SubtitleMode.PREFERRED_ALWAYS -> preferredTrack ?: forced
            SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS ->
                if (preferred != null && audioLanguage.languageMatches(preferred)) forced else preferredTrack ?: forced
        }
    }
}

internal fun String?.languageMatches(other: String?): Boolean {
    val left = normalizedLanguageCode() ?: return false
    val right = other.normalizedLanguageCode() ?: return false
    return left == right
}

private fun String?.normalizedLanguageCode(): String? {
    val value = this?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (value.substringBefore('-').substringBefore('_')) {
        "en", "eng" -> "eng"
        "de", "deu", "ger" -> "deu"
        "ja", "jpn" -> "jpn"
        "fr", "fra", "fre" -> "fra"
        "es", "spa" -> "spa"
        "it", "ita" -> "ita"
        "ko", "kor" -> "kor"
        "zh", "zho", "chi" -> "zho"
        "ar", "ara" -> "ara"
        "pt", "por" -> "por"
        "ru", "rus" -> "rus"
        "nl", "nld", "dut" -> "nld"
        else -> value
    }
}
