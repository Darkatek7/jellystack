package app.jellystack.tv.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItemDetailStore
import dev.jellystack.core.jellyfin.JellyfinItemStore
import dev.jellystack.core.jellyfin.JellyfinLibraryStore
import dev.jellystack.core.jellyfin.JellystackClientVersionProvider
import dev.jellystack.core.jellyfin.ServerRepositoryEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationStore
import dev.jellystack.core.jellyseerr.ServerRepositoryJellyseerrEnvironmentProvider
import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.ProfileEnvironmentProvider
import dev.jellystack.core.profile.ProfileMyListRepository
import dev.jellystack.core.profile.ProfilePinRepository
import dev.jellystack.core.profile.ProfilePreferencesRepository
import dev.jellystack.core.profile.ProfileRemovalCoordinator
import dev.jellystack.core.profile.ProfileStore
import dev.jellystack.core.server.ServerStore
import dev.jellystack.database.JellyfinFavoritesStore
import dev.jellystack.database.JellystackDatabase
import dev.jellystack.database.jellyfinItemDetailStore
import dev.jellystack.database.jellyfinItemStore
import dev.jellystack.database.jellyfinLibraryStore
import dev.jellystack.database.jellyseerrRecommendationStore
import dev.jellystack.database.profileStore
import dev.jellystack.database.serverStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val tvAppModule =
    module {
        single<SqlDriver> {
            AndroidSqliteDriver(
                schema = JellystackDatabase.Schema,
                context = androidContext(),
                name = "jellystack.db",
            )
        }
        single { JellystackDatabase(get()) }
        single<ServerStore> { get<JellystackDatabase>().serverStore() }
        single<ProfileStore> { get<JellystackDatabase>().profileStore() }
        single<JellyfinLibraryStore> { get<JellystackDatabase>().jellyfinLibraryStore() }
        single<JellyfinItemStore> { get<JellystackDatabase>().jellyfinItemStore() }
        single<JellyfinItemDetailStore> { get<JellystackDatabase>().jellyfinItemDetailStore() }
        single<JellyseerrRecommendationStore> { get<JellystackDatabase>().jellyseerrRecommendationStore() }
        single<JellyfinFavoritesStoreApi> { JellyfinFavoritesStore(get()) }
        single { ProfilePreferencesRepository(get()) }
        single { ProfileMyListRepository(get()) }
        single { ActiveProfileRepository(get()) }
        single { ProfilePinRepository(get()) }
        single {
            ProfileEnvironmentProvider(
                activeProfiles = get(),
                bindingResolver = get<ProfileStore>()::getBinding,
                serverResolver = get<dev.jellystack.core.server.ServerRepository>()::findServer,
                clientVersionProvider = { get<JellystackClientVersionProvider>().versionName() },
            )
        }
        single<JellyfinEnvironmentProvider> {
            ServerRepositoryEnvironmentProvider(
                repository = get(),
                clientVersionProvider = { get<JellystackClientVersionProvider>().versionName() },
                profileEnvironmentProvider = get(),
            )
        }
        single<JellyseerrEnvironmentProvider> {
            ServerRepositoryJellyseerrEnvironmentProvider(
                repository = get(),
                profileEnvironmentProvider = get(),
            )
        }
        single {
            ProfileRemovalCoordinator(
                store = get(),
                preferences = get(),
                pins = get(),
                removeLocalConnection = get<dev.jellystack.core.server.ServerRepository>()::remove,
            )
        }
        single {
            HouseholdProfileRepository(
                store = get(),
                activeServerPreferences = get(),
                legacyProfileMigration = get<ProfilePreferencesRepository>()::migrateLegacyProfile,
            )
        }
    }
