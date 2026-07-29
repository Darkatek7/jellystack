package dev.jellystack.players

import com.russhwolf.settings.Settings
import dev.jellystack.core.jellyfin.JellyfinItem

data class SubtitleTrackPreference(
    val disabled: Boolean,
    val language: String?,
    val title: String?,
    val format: SubtitleFormat?,
    val isDefault: Boolean,
    val isForced: Boolean,
    val streamIndex: Int?,
    val trackId: String?,
)

data class SubtitlePreferenceResolution(
    val trackId: String?,
    val disabled: Boolean,
)

data class AudioTrackPreference(
    val language: String?,
    val title: String?,
    val codec: String?,
    val isDefault: Boolean,
    val streamIndex: Int?,
    val trackId: String?,
)

interface SubtitlePreferenceStore {
    fun read(scopeKey: String): SubtitleTrackPreference?

    fun write(
        scopeKey: String,
        preference: SubtitleTrackPreference,
    )

    fun clearAll() = Unit

    fun readAudio(scopeKey: String): AudioTrackPreference? = null

    fun writeAudio(
        scopeKey: String,
        preference: AudioTrackPreference,
    ) = Unit
}

object NoopSubtitlePreferenceStore : SubtitlePreferenceStore {
    override fun read(scopeKey: String): SubtitleTrackPreference? = null

    override fun write(
        scopeKey: String,
        preference: SubtitleTrackPreference,
    ) = Unit
}

class SettingsSubtitlePreferenceStore(
    private val settings: Settings,
) : SubtitlePreferenceStore {
    override fun read(scopeKey: String): SubtitleTrackPreference? {
        val prefix = keyPrefix(scopeKey)
        if (!settings.hasKey("$prefix.disabled")) return null
        return SubtitleTrackPreference(
            disabled = settings.getBoolean("$prefix.disabled", false),
            language = settings.getNullableString("$prefix.language"),
            title = settings.getNullableString("$prefix.title"),
            format = settings.getNullableString("$prefix.format")?.let { runCatching { SubtitleFormat.valueOf(it) }.getOrNull() },
            isDefault = settings.getBoolean("$prefix.default", false),
            isForced = settings.getBoolean("$prefix.forced", false),
            streamIndex = settings.getNullableInt("$prefix.streamIndex"),
            trackId = settings.getNullableString("$prefix.trackId"),
        )
    }

    override fun write(
        scopeKey: String,
        preference: SubtitleTrackPreference,
    ) {
        val prefix = keyPrefix(scopeKey)
        settings.putBoolean("$prefix.disabled", preference.disabled)
        settings.putNullableString("$prefix.language", preference.language)
        settings.putNullableString("$prefix.title", preference.title)
        settings.putNullableString("$prefix.format", preference.format?.name)
        settings.putBoolean("$prefix.default", preference.isDefault)
        settings.putBoolean("$prefix.forced", preference.isForced)
        settings.putNullableInt("$prefix.streamIndex", preference.streamIndex)
        settings.putNullableString("$prefix.trackId", preference.trackId)
    }

    override fun clearAll() {
        settings.keys
            .filter { it.startsWith("subtitle.preference.") || it.startsWith("audio.preference.") }
            .forEach(settings::remove)
    }

    override fun readAudio(scopeKey: String): AudioTrackPreference? {
        val prefix = audioKeyPrefix(scopeKey)
        if (!settings.hasKey("$prefix.trackId")) return null
        return AudioTrackPreference(
            language = settings.getNullableString("$prefix.language"),
            title = settings.getNullableString("$prefix.title"),
            codec = settings.getNullableString("$prefix.codec"),
            isDefault = settings.getBoolean("$prefix.default", false),
            streamIndex = settings.getNullableInt("$prefix.streamIndex"),
            trackId = settings.getNullableString("$prefix.trackId"),
        )
    }

    override fun writeAudio(
        scopeKey: String,
        preference: AudioTrackPreference,
    ) {
        val prefix = audioKeyPrefix(scopeKey)
        settings.putNullableString("$prefix.language", preference.language)
        settings.putNullableString("$prefix.title", preference.title)
        settings.putNullableString("$prefix.codec", preference.codec)
        settings.putBoolean("$prefix.default", preference.isDefault)
        settings.putNullableInt("$prefix.streamIndex", preference.streamIndex)
        settings.putNullableString("$prefix.trackId", preference.trackId)
    }

    private fun keyPrefix(scopeKey: String): String = "subtitle.preference.${scopeKey.sanitizePreferenceKey()}"

    private fun audioKeyPrefix(scopeKey: String): String = "audio.preference.${scopeKey.sanitizePreferenceKey()}"
}

fun JellyfinItem.subtitlePreferenceScopeKey(): String =
    seriesId?.takeIf { it.isNotBlank() }?.let { "series:$it" }
        ?: seriesName?.takeIf { it.isNotBlank() }?.normalizePreferenceText()?.let { "series-name:$it" }
        ?: "item:$id"

fun PlaybackMetadata.subtitlePreferenceScopeKey(mediaId: String): String =
    seriesId?.takeIf { it.isNotBlank() }?.let { "series:$it" }
        ?: seriesName?.takeIf { it.isNotBlank() }?.normalizePreferenceText()?.let { "series-name:$it" }
        ?: "item:$mediaId"

fun SubtitleTrack.toPreference(): SubtitleTrackPreference =
    SubtitleTrackPreference(
        disabled = false,
        language = language,
        title = title,
        format = format,
        isDefault = isDefault,
        isForced = isForced,
        streamIndex = streamIndex,
        trackId = id,
    )

fun AudioTrack.toPreference(): AudioTrackPreference = AudioTrackPreference(language, title, codec, isDefault, streamIndex, id)

fun List<AudioTrack>.resolveAudioPreference(preference: AudioTrackPreference): AudioTrack? =
    firstOrNull { it.language.languageMatches(preference.language) && it.title.matchesPreference(preference.title) }
        ?: firstOrNull { it.language.languageMatches(preference.language) && it.codec.matchesPreference(preference.codec) }
        ?: firstOrNull { it.language.languageMatches(preference.language) }
        ?: firstOrNull { it.streamIndex != null && it.streamIndex == preference.streamIndex }
        ?: firstOrNull { it.id == preference.trackId }

fun disabledSubtitlePreference(): SubtitleTrackPreference =
    SubtitleTrackPreference(
        disabled = true,
        language = null,
        title = null,
        format = null,
        isDefault = false,
        isForced = false,
        streamIndex = null,
        trackId = null,
    )

fun List<SubtitleTrack>.resolveSubtitlePreference(preference: SubtitleTrackPreference): SubtitlePreferenceResolution {
    if (preference.disabled) {
        return SubtitlePreferenceResolution(trackId = null, disabled = true)
    }
    val resolved =
        firstOrNull { track -> track.matchesLanguageTitleFlags(preference) }
            ?: firstOrNull { track -> track.matchesLanguageFlagsFormat(preference) }
            ?: firstOrNull { track -> track.matchesLanguageFlags(preference) }
            ?: firstOrNull { track -> track.streamIndex != null && track.streamIndex == preference.streamIndex }
            ?: firstOrNull { track -> track.id == preference.trackId }
    return SubtitlePreferenceResolution(trackId = resolved?.id, disabled = false)
}

private fun SubtitleTrack.matchesLanguageTitleFlags(preference: SubtitleTrackPreference): Boolean =
    language.matchesPreference(preference.language) &&
        title.matchesPreference(preference.title) &&
        isForced == preference.isForced &&
        isDefault == preference.isDefault

private fun SubtitleTrack.matchesLanguageFlagsFormat(preference: SubtitleTrackPreference): Boolean =
    language.matchesPreference(preference.language) &&
        isForced == preference.isForced &&
        format == preference.format

private fun SubtitleTrack.matchesLanguageFlags(preference: SubtitleTrackPreference): Boolean =
    language.matchesPreference(preference.language) &&
        isForced == preference.isForced

private fun String?.matchesPreference(other: String?): Boolean = normalizePreferenceText() == other.normalizePreferenceText()

private fun String?.normalizePreferenceText(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

private fun String.sanitizePreferenceKey(): String =
    map { char -> if (char.isLetterOrDigit() || char == ':' || char == '-' || char == '_') char else '_' }
        .joinToString("")

private fun Settings.getNullableString(key: String): String? = if (hasKey(key)) getString(key, "") else null

private fun Settings.putNullableString(
    key: String,
    value: String?,
) {
    if (value == null) {
        remove(key)
    } else {
        putString(key, value)
    }
}

private fun Settings.getNullableInt(key: String): Int? = if (hasKey(key)) getInt(key, 0) else null

private fun Settings.putNullableInt(
    key: String,
    value: Int?,
) {
    if (value == null) {
        remove(key)
    } else {
        putInt(key, value)
    }
}
