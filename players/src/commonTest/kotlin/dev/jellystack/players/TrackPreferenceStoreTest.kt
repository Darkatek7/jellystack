package dev.jellystack.players

import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackPreferenceStoreTest {
    @Test
    fun audioPreferenceSurvivesTrackIdChangesAndClearRemovesBothKinds() {
        val store = SettingsSubtitlePreferenceStore(TestSettings())
        val original = AudioTrack("3", "deu", "German", "aac", false, 3)
        store.writeAudio("series:one", original.toPreference())
        store.write(
            "series:one",
            SubtitleTrack("4", "eng", "English", SubtitleFormat.SRT, false, false, 4).toPreference(),
        )

        val current = listOf(AudioTrack("9", "de", "German", "aac", false, 9))
        assertEquals("9", store.readAudio("series:one")?.let(current::resolveAudioPreference)?.id)

        store.clearAll()
        assertNull(store.readAudio("series:one"))
        assertNull(store.read("series:one"))
    }
}

private class TestSettings : Settings {
    private val values = mutableMapOf<String, Any>()
    override val keys get() = values.keys
    override val size get() = values.size

    override fun clear() = values.clear()

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun hasKey(key: String) = key in values

    override fun putInt(
        key: String,
        value: Int,
    ) {
        values[key] = value
    }

    override fun getInt(
        key: String,
        defaultValue: Int,
    ) = values[key] as? Int ?: defaultValue

    override fun getIntOrNull(key: String) = values[key] as? Int

    override fun putLong(
        key: String,
        value: Long,
    ) {
        values[key] = value
    }

    override fun getLong(
        key: String,
        defaultValue: Long,
    ) = values[key] as? Long ?: defaultValue

    override fun getLongOrNull(key: String) = values[key] as? Long

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ) = values[key] as? String ?: defaultValue

    override fun getStringOrNull(key: String) = values[key] as? String

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        values[key] = value
    }

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ) = values[key] as? Float ?: defaultValue

    override fun getFloatOrNull(key: String) = values[key] as? Float

    override fun putDouble(
        key: String,
        value: Double,
    ) {
        values[key] = value
    }

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ) = values[key] as? Double ?: defaultValue

    override fun getDoubleOrNull(key: String) = values[key] as? Double

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ) = values[key] as? Boolean ?: defaultValue

    override fun getBooleanOrNull(key: String) = values[key] as? Boolean
}
