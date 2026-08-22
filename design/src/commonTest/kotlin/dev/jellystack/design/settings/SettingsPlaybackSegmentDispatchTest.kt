package dev.jellystack.design.settings

import com.russhwolf.settings.Settings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.SegmentSkipMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsPlaybackSegmentDispatchTest {
    @Test
    fun allFiveSettingsActionsPersistThroughProductionDispatcher() {
        val storage = MemorySettings()
        val repository = AppSettingsRepository(storage)
        val actions =
            listOf(
                SettingsAction.SetIntroSkipMode(SegmentSkipMode.AUTO_SKIP),
                SettingsAction.SetRecapSkipMode(SegmentSkipMode.OFF),
                SettingsAction.SetOutroSkipMode(SegmentSkipMode.AUTO_SKIP),
                SettingsAction.SetPreviewSkipMode(SegmentSkipMode.SHOW_BUTTON),
                SettingsAction.SetCommercialSkipMode(SegmentSkipMode.SHOW_BUTTON),
            )

        actions.forEach { action ->
            assertTrue(persistPlaybackSegmentSetting(action, repository))
        }

        val restored = AppSettingsRepository(storage).settings.value
        assertEquals(SegmentSkipMode.AUTO_SKIP, restored.introSkipMode)
        assertEquals(SegmentSkipMode.OFF, restored.recapSkipMode)
        assertEquals(SegmentSkipMode.AUTO_SKIP, restored.outroSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, restored.previewSkipMode)
        assertEquals(SegmentSkipMode.SHOW_BUTTON, restored.commercialSkipMode)
    }
}

private class MemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = values.keys
    override val size: Int get() = values.size

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
