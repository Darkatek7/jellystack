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
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.design.tv.TvJellystackRoot
import dev.jellystack.players.AndroidPlaybackSessionBridge
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.AndroidTvPlaybackDeviceProfileProvider
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
    private lateinit var trailerPreviewEngine: AndroidPlayerEngine
    private lateinit var trailerPreviewController: PlaybackController
    private lateinit var platformActions: TvPlatformActionCoordinator<KeyEvent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setViewTreeNavigationEventDispatcherOwner(this)
        playerEngine =
            AndroidPlayerEngine(
                context = applicationContext,
                preferHighestSupportedBitrate = true,
            )
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
                        deviceProfileProvider = AndroidTvPlaybackDeviceProfileProvider(),
                        clientVersion = BuildConfig.VERSION_NAME,
                    ),
                playerEngine = playerEngine,
                streamingProgressReporter = JellyfinStreamingProgressReporter(browseRepository),
                subtitlePreferenceStore = SettingsSubtitlePreferenceStore(playbackSettings),
                playbackPreferencesProvider = PlaybackPreferencesProvider { settingsRepository.settings.value },
            )
        trailerPreviewController = createTrailerPreviewController(settingsRepository)
        playbackBridge =
            AndroidPlaybackSessionBridge(
                context = this,
                engine = playerEngine,
                controller = playbackController,
                seekBackMs = { settingsRepository.settings.value.seekBackSeconds * 1_000L },
                seekForwardMs = { settingsRepository.settings.value.seekForwardSeconds * 1_000L },
            )
        platformActions =
            TvPlatformActionCoordinator(
                object : TvPlatformActions<KeyEvent> {
                    override fun setKeepScreenOn(enabled: Boolean) {
                        if (enabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    override fun markPlaybackStarted() = playbackBridge.markPlaybackStarted()

                    override fun handleMediaKey(event: KeyEvent): Boolean = playbackBridge.handleMediaKeyEvent(event)

                    override fun stopTrailer() = trailerPreviewController.stop(saveProgress = false)

                    override fun stopPlayback() = playbackBridge.stopPlayback()

                    override fun releaseTrailer() = trailerPreviewController.release()

                    override fun releaseBridge() = playbackBridge.release()

                    override fun releasePlayback() = playbackController.release()
                },
            )
        lifecycleScope.launch {
            playbackController.state.collect { state ->
                val playbackIsActive = state is PlaybackState.Active || state is PlaybackState.Preparing
                platformActions.onPlaybackActivityChanged(playbackIsActive)
            }
        }
        setContent {
            TvJellystackRoot(
                playbackController = playbackController,
                playerEngine = playerEngine,
                trailerPreviewController = trailerPreviewController,
                trailerPreviewEngine = trailerPreviewEngine,
                appVersion = BuildConfig.VERSION_NAME,
                stopPlayback = playbackBridge::stopPlayback,
            )
        }
    }

    private fun createTrailerPreviewController(settingsRepository: AppSettingsRepository): PlaybackController {
        trailerPreviewEngine = AndroidPlayerEngine(context = applicationContext)
        return PlaybackController(
            playbackSourceResolver =
                JellyfinPlaybackSourceResolver(
                    playbackInfoService = NetworkJellyfinPlaybackInfoService(),
                    deviceProfileProvider = AndroidTvPlaybackDeviceProfileProvider(),
                    clientVersion = BuildConfig.VERSION_NAME,
                ),
            playerEngine = trailerPreviewEngine,
            playbackPreferencesProvider =
                PlaybackPreferencesProvider {
                    settingsRepository.settings.value.copy(
                        wifiStreamingQuality = StreamingQualityPreference.MBPS_4_720P,
                        mobileStreamingQuality = StreamingQualityPreference.MBPS_4_720P,
                        resumeMode = ResumeMode.RESTART,
                        preferredSubtitleLanguage = null,
                        subtitleMode = SubtitleMode.OFF,
                        rememberSeriesTracks = false,
                    )
                },
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPlaybackVisible = playbackController.state.value is PlaybackState.Active
        if (platformActions.dispatchMediaKey(event, isPlaybackVisible)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        platformActions.onStop(isChangingConfigurations)
        super.onStop()
    }

    override fun onDestroy() {
        platformActions.onDestroy()
        super.onDestroy()
    }
}
