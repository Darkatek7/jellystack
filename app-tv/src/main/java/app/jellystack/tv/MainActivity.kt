package app.jellystack.tv

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.playback.JellyfinStreamingProgressReporter
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.design.tv.TvJellystackRoot
import dev.jellystack.players.AndroidPlaybackDeviceProfileProvider
import dev.jellystack.players.AndroidPlaybackSessionBridge
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.JellyfinPlaybackSourceResolver
import dev.jellystack.players.NetworkJellyfinPlaybackInfoService
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackPreferencesProvider
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SettingsPlaybackProgressStore
import dev.jellystack.players.SettingsSubtitlePreferenceStore
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private lateinit var playerEngine: AndroidPlayerEngine
    private lateinit var playbackController: PlaybackController
    private lateinit var playbackBridge: AndroidPlaybackSessionBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setViewTreeNavigationEventDispatcherOwner(this)
        playerEngine = AndroidPlayerEngine(applicationContext)
        val koin = JellystackDI.koin
        val settingsRepository = koin.get<AppSettingsRepository>()
        val browseRepository = koin.get<JellyfinBrowseRepository>()
        val playbackSettings =
            SharedPreferencesSettings(
                getSharedPreferences("jellystack_playback", Context.MODE_PRIVATE),
            )
        playbackController =
            PlaybackController(
                progressStore = SettingsPlaybackProgressStore(playbackSettings),
                playbackSourceResolver =
                    JellyfinPlaybackSourceResolver(
                        playbackInfoService = NetworkJellyfinPlaybackInfoService(),
                        deviceProfileProvider = AndroidPlaybackDeviceProfileProvider(),
                        clientVersion = BuildConfig.VERSION_NAME,
                    ),
                playerEngine = playerEngine,
                streamingProgressReporter = JellyfinStreamingProgressReporter(browseRepository),
                subtitlePreferenceStore = SettingsSubtitlePreferenceStore(playbackSettings),
                playbackPreferencesProvider = PlaybackPreferencesProvider { settingsRepository.settings.value },
            )
        playbackBridge =
            AndroidPlaybackSessionBridge(
                context = this,
                engine = playerEngine,
                controller = playbackController,
                seekBackMs = { settingsRepository.settings.value.seekBackSeconds * 1_000L },
                seekForwardMs = { settingsRepository.settings.value.seekForwardSeconds * 1_000L },
            )
        lifecycleScope.launch {
            var playbackWasActive = false
            playbackController.state.collect { state ->
                val playbackIsActive = state is PlaybackState.Active || state is PlaybackState.Preparing
                if (playbackIsActive) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                if (playbackIsActive && !playbackWasActive) playbackBridge.markPlaybackStarted()
                playbackWasActive = playbackIsActive
            }
        }
        setContent {
            TvJellystackRoot(
                playbackController = playbackController,
                playerEngine = playerEngine,
                appVersion = BuildConfig.VERSION_NAME,
                stopPlayback = playbackBridge::stopPlayback,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPlaybackVisible = playbackController.state.value is PlaybackState.Active
        if (isPlaybackVisible && playbackBridge.handleMediaKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        if (!isChangingConfigurations) playbackBridge.stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        playbackBridge.release()
        playbackController.release()
        super.onDestroy()
    }
}
