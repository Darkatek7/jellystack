package dev.jellystack.core.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import dev.jellystack.core.jellyfin.JellystackClientVersionProvider
import dev.jellystack.core.security.SecureStoreEngine
import dev.jellystack.core.security.SecureStoreEngineFactory
import dev.jellystack.core.security.ios.IosSecureStoreEngine
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

actual fun platformModule() =
    module {
        single<SecureStoreEngineFactory> { IosSecureStoreEngineFactory() }
        single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults()) }
        single<JellystackClientVersionProvider> {
            JellystackClientVersionProvider {
                (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: "unknown"
            }
        }
    }

private class IosSecureStoreEngineFactory : SecureStoreEngineFactory {
    override fun create(name: String): SecureStoreEngine = IosSecureStoreEngine(name)
}
