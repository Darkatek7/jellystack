package dev.jellystack.core.profile

import com.russhwolf.settings.Settings
import dev.jellystack.core.jellyfin.LibraryBrowseQuery
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.SubtitleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ProfilePreferences(
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.SERVER_DEFAULT,
    val rememberSeriesTracks: Boolean = true,
    val autoplayNextMode: AutoplayNextMode = AutoplayNextMode.COUNTDOWN,
    val resumeMode: ResumeMode = ResumeMode.RESUME,
    val defaultPlaybackSpeed: Float = 1f,
    val homeShelfOrder: List<String> = emptyList(),
) {
    fun applyTo(deviceSettings: AppSettings): AppSettings =
        deviceSettings.copy(
            preferredAudioLanguage = preferredAudioLanguage,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
            subtitleMode = subtitleMode,
            rememberSeriesTracks = rememberSeriesTracks,
            autoplayNextMode = autoplayNextMode,
            resumeMode = resumeMode,
            defaultPlaybackSpeed = defaultPlaybackSpeed,
        )
}

class ProfilePreferencesRepository(
    private val storage: Settings,
) {
    private val states = mutableMapOf<String, MutableStateFlow<ProfilePreferences>>()
    private val browseQueryStates = mutableMapOf<Pair<String, String>, MutableStateFlow<LibraryBrowseQuery>>()

    fun preferences(profileId: String): StateFlow<ProfilePreferences> = stateFor(profileId).asStateFlow()

    fun libraryBrowseQuery(
        profileId: String,
        libraryId: String,
    ): StateFlow<LibraryBrowseQuery> = browseQueryState(profileId, libraryId).asStateFlow()

    fun setLibraryBrowseQuery(
        profileId: String,
        libraryId: String,
        query: LibraryBrowseQuery,
    ) {
        val state = browseQueryState(profileId, libraryId)
        val storageKey = browseQueryKey(profileId, libraryId)
        if (query.isDefault) {
            storage.remove(storageKey)
        } else {
            storage.putString(storageKey, JSON.encodeToString(query))
        }
        state.value = query
    }

    fun setPreferredAudioLanguage(
        profileId: String,
        value: String?,
    ) = updateLanguage(profileId, KEY_AUDIO_LANGUAGE, value) { copy(preferredAudioLanguage = it) }

    fun setPreferredSubtitleLanguage(
        profileId: String,
        value: String?,
    ) = updateLanguage(profileId, KEY_SUBTITLE_LANGUAGE, value) { copy(preferredSubtitleLanguage = it) }

    fun setSubtitleMode(
        profileId: String,
        value: SubtitleMode,
    ) = update(profileId, KEY_SUBTITLE_MODE, value.name) { copy(subtitleMode = value) }

    fun setRememberSeriesTracks(
        profileId: String,
        value: Boolean,
    ) = update(profileId, KEY_REMEMBER_SERIES_TRACKS, value) { copy(rememberSeriesTracks = value) }

    fun setAutoplayNextMode(
        profileId: String,
        value: AutoplayNextMode,
    ) = update(profileId, KEY_AUTOPLAY_NEXT, value.name) { copy(autoplayNextMode = value) }

    fun setResumeMode(
        profileId: String,
        value: ResumeMode,
    ) = update(profileId, KEY_RESUME_MODE, value.name) { copy(resumeMode = value) }

    fun setDefaultPlaybackSpeed(
        profileId: String,
        value: Float,
    ) {
        val normalized = value.takeIf(PLAYBACK_SPEEDS::contains) ?: 1f
        storage.putFloat(key(profileId, KEY_DEFAULT_PLAYBACK_SPEED), normalized)
        publish(profileId) { copy(defaultPlaybackSpeed = normalized) }
    }

    fun setHomeShelfOrder(
        profileId: String,
        value: List<String>,
    ) {
        val normalized = value.map(String::trim).filter(String::isNotEmpty).distinct()
        val storageKey = key(profileId, KEY_HOME_SHELF_ORDER)
        if (normalized.isEmpty()) storage.remove(storageKey) else storage.putString(storageKey, normalized.joinToString(SHELF_SEPARATOR))
        publish(profileId) { copy(homeShelfOrder = normalized) }
    }

    fun migrateLegacyProfile(profileId: String): Boolean {
        requireProfileId(profileId)
        val marker = key(profileId, KEY_LEGACY_MIGRATED)
        if (storage.getBoolean(marker, false)) return false
        val migrated =
            ProfilePreferences(
                preferredAudioLanguage = storage.getStringOrNull(LEGACY_AUDIO_LANGUAGE).normalizedLanguage(),
                preferredSubtitleLanguage = storage.getStringOrNull(LEGACY_SUBTITLE_LANGUAGE).normalizedLanguage(),
                subtitleMode = legacyEnum(LEGACY_SUBTITLE_MODE, SubtitleMode.SERVER_DEFAULT),
                rememberSeriesTracks = storage.getBoolean(LEGACY_REMEMBER_SERIES_TRACKS, true),
                autoplayNextMode = legacyEnum(LEGACY_AUTOPLAY_NEXT, AutoplayNextMode.COUNTDOWN),
                resumeMode = legacyEnum(LEGACY_RESUME_MODE, ResumeMode.RESUME),
                defaultPlaybackSpeed =
                    storage.getFloat(LEGACY_DEFAULT_PLAYBACK_SPEED, 1f).takeIf(PLAYBACK_SPEEDS::contains) ?: 1f,
            )
        writeAll(profileId, migrated)
        storage.putBoolean(marker, true)
        stateFor(profileId).value = migrated
        return true
    }

    fun delete(profileId: String) {
        requireProfileId(profileId)
        storage.keys.filter { it.startsWith(prefix(profileId)) }.forEach(storage::remove)
        states.remove(profileId)?.value = ProfilePreferences()
        browseQueryStates.keys.filter { it.first == profileId }.forEach { key ->
            browseQueryStates.remove(key)?.value = LibraryBrowseQuery.DEFAULT
        }
    }

    private fun stateFor(profileId: String): MutableStateFlow<ProfilePreferences> {
        requireProfileId(profileId)
        return states.getOrPut(profileId) { MutableStateFlow(read(profileId)) }
    }

    private fun browseQueryState(
        profileId: String,
        libraryId: String,
    ): MutableStateFlow<LibraryBrowseQuery> {
        requireLibraryId(libraryId)
        return browseQueryStates.getOrPut(profileId to libraryId) {
            val stored = storage.getStringOrNull(browseQueryKey(profileId, libraryId))
            MutableStateFlow(
                stored?.let { runCatching { JSON.decodeFromString<LibraryBrowseQuery>(it) }.getOrNull() } ?: LibraryBrowseQuery.DEFAULT,
            )
        }
    }

    private fun read(profileId: String): ProfilePreferences =
        ProfilePreferences(
            preferredAudioLanguage = storage.getStringOrNull(key(profileId, KEY_AUDIO_LANGUAGE)).normalizedLanguage(),
            preferredSubtitleLanguage = storage.getStringOrNull(key(profileId, KEY_SUBTITLE_LANGUAGE)).normalizedLanguage(),
            subtitleMode = enumValue(profileId, KEY_SUBTITLE_MODE, SubtitleMode.SERVER_DEFAULT),
            rememberSeriesTracks = storage.getBoolean(key(profileId, KEY_REMEMBER_SERIES_TRACKS), true),
            autoplayNextMode = enumValue(profileId, KEY_AUTOPLAY_NEXT, AutoplayNextMode.COUNTDOWN),
            resumeMode = enumValue(profileId, KEY_RESUME_MODE, ResumeMode.RESUME),
            defaultPlaybackSpeed =
                storage.getFloat(key(profileId, KEY_DEFAULT_PLAYBACK_SPEED), 1f).takeIf(PLAYBACK_SPEEDS::contains) ?: 1f,
            homeShelfOrder =
                storage
                    .getStringOrNull(key(profileId, KEY_HOME_SHELF_ORDER))
                    ?.split(SHELF_SEPARATOR)
                    ?.map(String::trim)
                    ?.filter(String::isNotEmpty)
                    ?.distinct()
                    .orEmpty(),
        )

    private fun writeAll(
        profileId: String,
        value: ProfilePreferences,
    ) {
        writeLanguage(profileId, KEY_AUDIO_LANGUAGE, value.preferredAudioLanguage)
        writeLanguage(profileId, KEY_SUBTITLE_LANGUAGE, value.preferredSubtitleLanguage)
        storage.putString(key(profileId, KEY_SUBTITLE_MODE), value.subtitleMode.name)
        storage.putBoolean(key(profileId, KEY_REMEMBER_SERIES_TRACKS), value.rememberSeriesTracks)
        storage.putString(key(profileId, KEY_AUTOPLAY_NEXT), value.autoplayNextMode.name)
        storage.putString(key(profileId, KEY_RESUME_MODE), value.resumeMode.name)
        storage.putFloat(key(profileId, KEY_DEFAULT_PLAYBACK_SPEED), value.defaultPlaybackSpeed)
    }

    private inline fun <reified T : Enum<T>> enumValue(
        profileId: String,
        suffix: String,
        default: T,
    ): T = storage.getStringOrNull(key(profileId, suffix))?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private inline fun <reified T : Enum<T>> legacyEnum(
        key: String,
        default: T,
    ): T = storage.getStringOrNull(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private fun update(
        profileId: String,
        suffix: String,
        value: String,
        block: ProfilePreferences.() -> ProfilePreferences,
    ) {
        storage.putString(key(profileId, suffix), value)
        publish(profileId, block)
    }

    private fun update(
        profileId: String,
        suffix: String,
        value: Boolean,
        block: ProfilePreferences.() -> ProfilePreferences,
    ) {
        storage.putBoolean(key(profileId, suffix), value)
        publish(profileId, block)
    }

    private fun updateLanguage(
        profileId: String,
        suffix: String,
        value: String?,
        block: ProfilePreferences.(String?) -> ProfilePreferences,
    ) {
        val normalized = value.normalizedLanguage()
        writeLanguage(profileId, suffix, normalized)
        stateFor(profileId).value = stateFor(profileId).value.block(normalized)
    }

    private fun writeLanguage(
        profileId: String,
        suffix: String,
        value: String?,
    ) {
        val storageKey = key(profileId, suffix)
        if (value == null) storage.remove(storageKey) else storage.putString(storageKey, value)
    }

    private fun publish(
        profileId: String,
        block: ProfilePreferences.() -> ProfilePreferences,
    ) {
        val state = stateFor(profileId)
        state.value = state.value.block()
    }

    private fun key(
        profileId: String,
        suffix: String,
    ): String {
        requireProfileId(profileId)
        return "${prefix(profileId)}$suffix"
    }

    private fun prefix(profileId: String) = "profile.$profileId."

    private fun browseQueryKey(
        profileId: String,
        libraryId: String,
    ): String = "${prefix(profileId)}library_query.$libraryId"

    private fun requireProfileId(profileId: String) {
        require(profileId.isNotBlank() && profileId.none { it == '.' || it == '\n' || it == '\r' })
    }

    private fun requireLibraryId(libraryId: String) {
        require(libraryId.isNotBlank() && libraryId.none { it == '\n' || it == '\r' })
    }

    private fun String?.normalizedLanguage(): String? = this?.trim()?.lowercase()?.takeIf(String::isNotBlank)

    private companion object {
        const val KEY_AUDIO_LANGUAGE = "audio_language"
        const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        const val KEY_SUBTITLE_MODE = "subtitle_mode"
        const val KEY_REMEMBER_SERIES_TRACKS = "remember_series_tracks"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next"
        const val KEY_RESUME_MODE = "resume_mode"
        const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        const val KEY_HOME_SHELF_ORDER = "home_shelf_order"
        const val KEY_LEGACY_MIGRATED = "legacy_settings_migrated"
        const val LEGACY_AUDIO_LANGUAGE = "settings.audio_language"
        const val LEGACY_SUBTITLE_LANGUAGE = "settings.subtitle_language"
        const val LEGACY_SUBTITLE_MODE = "settings.subtitle_mode"
        const val LEGACY_REMEMBER_SERIES_TRACKS = "settings.remember_series_tracks"
        const val LEGACY_AUTOPLAY_NEXT = "settings.autoplay_next"
        const val LEGACY_RESUME_MODE = "settings.resume_mode"
        const val LEGACY_DEFAULT_PLAYBACK_SPEED = "settings.default_playback_speed"
        const val SHELF_SEPARATOR = "\u001f"
        val PLAYBACK_SPEEDS = setOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
