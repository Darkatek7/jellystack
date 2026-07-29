package dev.jellystack.core.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.core.security.SecureStoreEngine
import dev.jellystack.core.security.SecureStoreEngineFactory
import dev.jellystack.core.security.android.AndroidSecureStoreEngine
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() =
    module {
        single<SecureStoreEngineFactory> {
            AndroidSecureStoreEngineFactory(androidContext())
        }
        single<Settings> {
            val context = androidContext()
            val preferences = context.getSharedPreferences("jellystack_prefs", Context.MODE_PRIVATE)
            SharedPreferencesSettings(preferences)
        }
    }

private class AndroidSecureStoreEngineFactory(
    private val context: Context,
) : SecureStoreEngineFactory {
    override fun create(name: String) =
        runCatching { AndroidSecureStoreEngine(context, name) }
            .getOrElse { error ->
                Napier.e(
                    tag = "SecureStore",
                    throwable = error,
                ) { "Encrypted storage initialization failed; credential storage is unavailable." }
                UnavailableSecureStoreEngine(error)
            }
}

private class UnavailableSecureStoreEngine(
    private val initializationFailure: Throwable,
) : SecureStoreEngine {
    override fun write(
        key: String,
        value: String,
    ): Nothing = unavailable()

    override fun read(key: String): Nothing = unavailable()

    override fun delete(key: String): Nothing = unavailable()

    private fun unavailable(): Nothing =
        throw IllegalStateException(
            "Protected credential storage is unavailable on this device.",
            initializationFailure,
        )
}
