package dev.jellystack.ios.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItemDetailStore
import dev.jellystack.core.jellyfin.JellyfinItemStore
import dev.jellystack.core.jellyfin.JellyfinLibraryStore
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationStore
import dev.jellystack.core.server.ServerStore
import dev.jellystack.database.JellyfinFavoritesStore
import dev.jellystack.database.JellystackDatabase
import dev.jellystack.database.jellyfinItemDetailStore
import dev.jellystack.database.jellyfinItemStore
import dev.jellystack.database.jellyfinLibraryStore
import dev.jellystack.database.jellyseerrRecommendationStore
import dev.jellystack.database.serverStore
import org.koin.dsl.module

val iosAppModule =
    module {
        single {
            NativeSqliteDriver(
                schema = JellystackDatabase.Schema,
                name = "jellystack.db",
            )
        }
        single { JellystackDatabase(get()) }
        single<ServerStore> { get<JellystackDatabase>().serverStore() }
        single<JellyfinLibraryStore> { get<JellystackDatabase>().jellyfinLibraryStore() }
        single<JellyfinItemStore> { get<JellystackDatabase>().jellyfinItemStore() }
        single<JellyfinItemDetailStore> { get<JellystackDatabase>().jellyfinItemDetailStore() }
        single<JellyseerrRecommendationStore> { get<JellystackDatabase>().jellyseerrRecommendationStore() }
        single<JellyfinFavoritesStoreApi> { JellyfinFavoritesStore(get()) }
    }
