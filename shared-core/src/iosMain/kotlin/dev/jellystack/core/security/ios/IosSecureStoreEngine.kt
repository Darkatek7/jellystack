@file:OptIn(com.russhwolf.settings.ExperimentalSettingsImplementation::class)

package dev.jellystack.core.security.ios

import com.russhwolf.settings.KeychainSettings
import dev.jellystack.core.security.SecureStoreEngine

class IosSecureStoreEngine(
    service: String,
) : SecureStoreEngine {
    private val settings = KeychainSettings(service)

    override fun write(
        key: String,
        value: String,
    ) {
        settings.putString(key, value)
    }

    override fun read(key: String): String? = settings.getStringOrNull(key)

    override fun delete(key: String) {
        settings.remove(key)
    }
}
