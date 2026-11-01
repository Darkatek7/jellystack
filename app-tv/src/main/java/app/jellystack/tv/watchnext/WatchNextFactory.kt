package app.jellystack.tv.watchnext

import android.content.Context
import android.os.Build
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.design.tv.TvWatchNextPublisher
import dev.jellystack.design.tv.UnsupportedTvWatchNextPublisher
import java.io.File

object WatchNextFactory {
    /** The authority [WatchNextArtProvider] is registered under in the manifest. */
    fun artAuthority(context: Context): String = "${context.packageName}.watchnext.art"

    /**
     * A publisher for the system Watch Next row, or one that does nothing where there is no such row (Fire TV,
     * devices without the Android TV provider, Android 7.x).
     */
    fun create(
        context: Context,
        environments: JellyfinEnvironmentProvider,
    ): TvWatchNextPublisher {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !WatchNextSupport.detect(appContext)) {
            return UnsupportedTvWatchNextPublisher
        }
        val preferences = appContext.getSharedPreferences("jellystack_watch_next", Context.MODE_PRIVATE)
        val state = SharedPreferencesWatchNextState(preferences)
        return AndroidWatchNextPublisher(
            gateway = TvProviderWatchNextGateway(appContext),
            state = state,
            art =
                CachedWatchNextArt(
                    directory = File(appContext.cacheDir, WatchNextArtFiles.CACHE_DIRECTORY),
                    authority = artAuthority(appContext),
                    salt = state.artSalt,
                    environments = environments,
                    fetcher = HttpUrlImageFetcher(),
                ),
        )
    }
}
