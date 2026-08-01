package dev.jellystack.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.downloads.IosOfflineDownloadManager
import dev.jellystack.core.downloads.SettingsOfflineDownloadQueueStore
import dev.jellystack.core.downloads.SettingsOfflineMediaStore
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.playback.JellyfinOfflineProgressSyncer
import dev.jellystack.core.playback.JellyfinStreamingProgressReporter
import dev.jellystack.core.playback.SettingsOfflinePlaybackEventStore
import dev.jellystack.core.security.BiometricAuthGate
import dev.jellystack.design.JellystackRoot
import dev.jellystack.ios.di.iosAppModule
import dev.jellystack.players.IosOfflinePlaybackSourceResolver
import dev.jellystack.players.IosPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.SettingsPlaybackProgressStore
import dev.jellystack.players.SettingsSubtitlePreferenceStore
import org.koin.core.context.startKoin
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

private const val DEFAULT_APP_VERSION = "0.15.0"

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun ComposeEntry() = ComposeEntry(DEFAULT_APP_VERSION)

@Suppress("FunctionName", "ktlint:standard:function-naming")
@Composable
fun ComposeEntry(appVersion: String) = ComposeEntryContent(appVersion)

@Composable
private fun ComposeEntryContent(appVersion: String) {
    if (!JellystackDI.isStarted()) {
        startKoin {
            modules(JellystackDI.modules + iosAppModule)
        }
    }
    val playerEngine = remember { IosPlayerEngine() }
    val playbackDefaults = remember { NSUserDefaults(suiteName = "dev.jellystack.playback") ?: NSUserDefaults.standardUserDefaults() }
    val downloadDefaults = remember { NSUserDefaults(suiteName = "dev.jellystack.downloads") ?: NSUserDefaults.standardUserDefaults() }
    val playbackSettings = remember(playbackDefaults) { NSUserDefaultsSettings(playbackDefaults) }
    val downloadSettings = remember(downloadDefaults) { NSUserDefaultsSettings(downloadDefaults) }
    val progressStore = remember(playbackSettings) { SettingsPlaybackProgressStore(playbackSettings) }
    val subtitlePreferenceStore = remember(playbackSettings) { SettingsSubtitlePreferenceStore(playbackSettings) }
    val mediaStore = remember(downloadSettings) { SettingsOfflineMediaStore(downloadSettings) }
    val queueStore = remember(downloadSettings) { SettingsOfflineDownloadQueueStore(downloadSettings) }
    val eventStore = remember(downloadSettings) { SettingsOfflinePlaybackEventStore(downloadSettings) }
    val koin = remember { JellystackDI.koin }
    val biometricGate = remember(koin) { koin.get<BiometricAuthGate>() }
    DisposableEffect(biometricGate) {
        val center = NSNotificationCenter.defaultCenter
        val foregroundObserver =
            center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                biometricGate.onAppForegrounded()
            }
        val backgroundObserver =
            center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                biometricGate.onAppBackgrounded()
            }
        biometricGate.onAppForegrounded()
        onDispose {
            center.removeObserver(foregroundObserver)
            center.removeObserver(backgroundObserver)
            biometricGate.onAppBackgrounded()
        }
    }
    val browseRepository = remember(koin) { koin.get<JellyfinBrowseRepository>() }
    val progressSyncer =
        remember(browseRepository, eventStore) {
            JellyfinOfflineProgressSyncer(
                repository = browseRepository,
                store = eventStore,
            )
        }
    val streamingReporter = remember(browseRepository) { JellyfinStreamingProgressReporter(browseRepository) }
    val downloadManager =
        remember(mediaStore, queueStore) {
            IosOfflineDownloadManager(
                mediaStore = mediaStore,
                queueStore = queueStore,
            )
        }
    val controller =
        remember(playerEngine, progressStore, subtitlePreferenceStore, mediaStore, progressSyncer, streamingReporter) {
            PlaybackController(
                progressStore = progressStore,
                playerEngine = playerEngine,
                offlineMediaStore = mediaStore,
                offlineSourceResolver = IosOfflinePlaybackSourceResolver(),
                offlineProgressSyncer = progressSyncer,
                streamingProgressReporter = streamingReporter,
                subtitlePreferenceStore = subtitlePreferenceStore,
            )
        }
    DisposableEffect(controller, downloadManager) {
        onDispose {
            controller.release()
            downloadManager.release()
        }
    }
    JellystackRoot(
        controller = controller,
        downloadManager = downloadManager,
        appVersion = appVersion,
    )
}
