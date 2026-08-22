package dev.jellystack.core.di

import dev.jellystack.core.config.ServerConfigRepository
import dev.jellystack.core.jellyfin.HomeSectionsApiFactory
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.JellyfinAdminRepository
import dev.jellystack.core.jellyfin.JellyfinBrowseApiFactory
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinSessionApiFactory
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellystackClientVersionProvider
import dev.jellystack.core.jellyfin.ServerRepositoryEnvironmentProvider
import dev.jellystack.core.jellyfin.defaultHomeSectionsApiFactory
import dev.jellystack.core.jellyfin.defaultJellyfinBrowseApiFactory
import dev.jellystack.core.jellyfin.defaultJellyfinSessionApiFactory
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.ServerRepositoryJellyseerrEnvironmentProvider
import dev.jellystack.core.logging.NoOpTelemetryTracker
import dev.jellystack.core.logging.TelemetryTracker
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.OnboardingPreferenceRepository
import dev.jellystack.core.preferences.ThemePreferenceRepository
import dev.jellystack.core.security.BiometricAuthGate
import dev.jellystack.core.security.BiometricLockPreferenceRepository
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.SecureStoreFactory
import dev.jellystack.core.security.SecureStoreFactory.Companion.DEFAULT_SECURE_STORE_NAME
import dev.jellystack.core.security.SecureStoreFactoryImpl
import dev.jellystack.core.security.SecureStoreLogger
import dev.jellystack.core.security.SecureStoreNapierLogger
import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerConnectivity
import dev.jellystack.core.server.ServerConnectivityChecker
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

internal val jellyfinBrowseApiFactoryQualifier = named("jellyfinBrowseApiFactory")
internal val homeSectionsApiFactoryQualifier = named("homeSectionsApiFactory")
internal val jellyfinSessionApiFactoryQualifier = named("jellyfinSessionApiFactory")

fun coreModule(): Module =
    module {
        single<SecureStoreLogger> { SecureStoreNapierLogger() }
        single<CoroutineDispatcher> { Dispatchers.Default }
        single<TelemetryTracker> { NoOpTelemetryTracker() }
        single<SecureStoreFactory> {
            SecureStoreFactoryImpl(
                engineFactory = get(),
                logger = get(),
                dispatcher = get(),
            )
        }
        single<SecureStore> {
            get<SecureStoreFactory>().create(DEFAULT_SECURE_STORE_NAME)
        }
        single { BiometricLockPreferenceRepository(get()) }
        single { BiometricAuthGate(preferences = get(), dispatcher = get()) }
        single { ServerConfigRepository(secureStore = get()) }
        single<ServerConnectivity> { ServerConnectivityChecker() }
        single { ServerCredentialVault(get()) }
        single { ActiveServerPreferenceRepository(get()) }
        single {
            ServerRepository(
                store = get(),
                connectivity = get(),
                credentialVault = get(),
                activeServerPreferences = get(),
            )
        }
        single<JellyfinEnvironmentProvider> {
            ServerRepositoryEnvironmentProvider(
                repository = get(),
                clientVersionProvider = { get<JellystackClientVersionProvider>().versionName() },
            )
        }
        single<JellyseerrEnvironmentProvider> { ServerRepositoryJellyseerrEnvironmentProvider(get()) }
        single<JellyfinBrowseApiFactory>(jellyfinBrowseApiFactoryQualifier) { defaultJellyfinBrowseApiFactory() }
        single {
            JellyfinBrowseRepository(
                environmentProvider = get(),
                libraryStore = get(),
                itemStore = get(),
                detailStore = get(),
                apiFactory = get(jellyfinBrowseApiFactoryQualifier),
            )
        }
        single<HomeSectionsApiFactory>(homeSectionsApiFactoryQualifier) { defaultHomeSectionsApiFactory() }
        single {
            HomeSectionsRepository(
                environmentProvider = get(),
                apiFactory = get(homeSectionsApiFactoryQualifier),
            )
        }
        single<JellyfinSessionApiFactory>(jellyfinSessionApiFactoryQualifier) { defaultJellyfinSessionApiFactory() }
        single {
            JellyfinSessionRepository(
                environmentProvider = get(),
                apiFactory = get(jellyfinSessionApiFactoryQualifier),
            )
        }
        single { JellyfinAdminRepository(get()) }
        single { JellyseerrRepository(recommendationsStore = get()) }
        single { JellyseerrAuthenticator() }
        single { ServerConnectionCoordinator(get(), get()) }
        single { JellyfinQuickConnectCoordinator(get()) }
        single { ThemePreferenceRepository(get()) }
        single { AppSettingsRepository(get()) }
        single { OnboardingPreferenceRepository(get()) }
    }

expect fun platformModule(): Module

fun sharedModules(): List<Module> = listOf(coreModule(), platformModule())

object JellystackDI {
    val modules: List<Module> get() = sharedModules()
    val koin by lazy { KoinPlatformTools.defaultContext().get() }

    fun isStarted(): Boolean = KoinPlatformTools.defaultContext().getOrNull() != null
}
