package app.jellystack.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import app.jellystack.tv.watchnext.WatchNextFactory
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.playback.JellyfinStreamingProgressReporter
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.ProfilePreferencesRepository
import dev.jellystack.design.tv.TvDeepLinkInbox
import dev.jellystack.design.tv.TvJellystackRoot
import dev.jellystack.design.tv.TvPlatformIntegrations
import dev.jellystack.design.tv.TvWatchNextPublisher
import dev.jellystack.players.AndroidPlaybackSessionBridge
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.AndroidTvPlaybackDeviceProfileProvider
import dev.jellystack.players.JellyfinPlaybackSourceResolver
import dev.jellystack.players.NetworkJellyfinPlaybackInfoService
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackNetworkClass
import dev.jellystack.players.PlaybackNetworkClassifier
import dev.jellystack.players.PlaybackPreferencesProvider
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.SettingsPlaybackProgressStore
import dev.jellystack.players.SettingsSubtitlePreferenceStore
import dev.jellystack.players.TranscodeStopReporter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private lateinit var playerEngine: AndroidPlayerEngine
    private lateinit var playbackController: PlaybackController
    private lateinit var playbackBridge: AndroidPlaybackSessionBridge
    private lateinit var trailerPreviewEngine: AndroidPlayerEngine
    private lateinit var trailerPreviewController: PlaybackController
    private lateinit var platformActions: TvPlatformActionCoordinator<KeyEvent>
    private lateinit var voiceSearch: AndroidTvVoiceSearch
    private lateinit var watchNext: TvWatchNextPublisher
    private val deepLinks = TvDeepLinkInbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restore after process death starts a new household session, so it gets the cold-launch profile gate.
        val coldLaunch = savedInstanceState == null || !processStarted
        processStarted = true
        window.decorView.setViewTreeNavigationEventDispatcherOwner(this)
        voiceSearch = AndroidTvVoiceSearch.create(this)
        playerEngine =
            AndroidPlayerEngine(
                context = applicationContext,
                preferHighestSupportedBitrate = true,
                softwareAudio = FfmpegSoftwareAudioDecoding,
            )
        val koin = JellystackDI.koin
        val settingsRepository = koin.get<AppSettingsRepository>()
        val activeProfileRepository = koin.get<ActiveProfileRepository>()
        val profilePreferencesRepository = koin.get<ProfilePreferencesRepository>()
        val browseRepository = koin.get<JellyfinBrowseRepository>()
        watchNext = WatchNextFactory.create(applicationContext, koin.get<JellyfinEnvironmentProvider>())
        // A restored activity still holds the intent it was started with; only a fresh start may act on it.
        if (savedInstanceState == null) offerDeepLink(intent)
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
                        deviceProfileProvider =
                            AndroidTvPlaybackDeviceProfileProvider(applicationContext, FfmpegSoftwareAudioDecoding),
                        clientVersion = BuildConfig.VERSION_NAME,
                    ),
                playerEngine = playerEngine,
                streamingProgressReporter = JellyfinStreamingProgressReporter(browseRepository),
                subtitlePreferenceStore = SettingsSubtitlePreferenceStore(playbackSettings),
                // TV exposes a single streaming-quality setting (stored as the Wi-Fi quality), so every
                // network counts as unmetered. Otherwise the mobile quality, which TV cannot change, wins.
                playbackNetworkClassifier = PlaybackNetworkClassifier { PlaybackNetworkClass.UNMETERED },
                playbackPreferencesProvider =
                    PlaybackPreferencesProvider {
                        val deviceSettings = settingsRepository.settings.value
                        activeProfileRepository.profileId.value
                            ?.let { profilePreferencesRepository.preferences(it).value.applyTo(deviceSettings) }
                            ?: deviceSettings
                    },
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
        platformActions = createPlatformActions()
        lifecycleScope.launch { clearWatchNextOnProfileChange(activeProfileRepository) }
        lifecycleScope.launch {
            val activity = playbackController.state.map(PlaybackState::screenActivity)
            launch { activity.map { it != TvScreenActivity.IDLE }.collect(platformActions::onPlaybackActivityChanged) }
            launch { activity.keepScreenOn().collect(platformActions::onKeepScreenOnChanged) }
        }
        setContent {
            TvJellystackRoot(
                playbackController = playbackController,
                playerEngine = playerEngine,
                trailerPreviewController = trailerPreviewController,
                trailerPreviewEngine = trailerPreviewEngine,
                appVersion = BuildConfig.VERSION_NAME,
                stopPlayback = playbackBridge::stopPlayback,
                coldLaunch = coldLaunch,
                voiceSearch = voiceSearch,
                integrations = TvPlatformIntegrations(watchNext = watchNext, deepLinks = deepLinks),
                onExitConfirmed = ::finish,
            )
        }
    }

    private fun createPlatformActions() =
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

    private fun createTrailerPreviewController(settingsRepository: AppSettingsRepository): PlaybackController {
        // The engine builds its ExoPlayer on the first trailer, so a session that never previews one pays nothing.
        trailerPreviewEngine =
            AndroidPlayerEngine(context = applicationContext, softwareAudio = FfmpegSoftwareAudioDecoding)
        val playbackInfoService = NetworkJellyfinPlaybackInfoService()
        return PlaybackController(
            playbackSourceResolver =
                JellyfinPlaybackSourceResolver(
                    playbackInfoService = playbackInfoService,
                    deviceProfileProvider =
                        AndroidTvPlaybackDeviceProfileProvider(applicationContext, FfmpegSoftwareAudioDecoding),
                    clientVersion = BuildConfig.VERSION_NAME,
                ),
            playerEngine = trailerPreviewEngine,
            // Previews stay out of the watch history; the reporter only stops the server transcode of a finished one.
            streamingProgressReporter =
                TranscodeStopReporter(playbackInfoService, JellystackDI.koin.get<JellyfinEnvironmentProvider>()),
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

    /** A profile switch or sign-out removes the previous profile cards from the system row. */
    private suspend fun clearWatchNextOnProfileChange(profiles: ActiveProfileRepository) {
        var previous: String? = null
        var first = true
        profiles.profileId.collect { current ->
            if (!first && previous != null && previous != current) watchNext.clear()
            first = false
            previous = current
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDeepLink(intent)
    }

    /** Hands a Watch Next card link to the app; the inbox ignores anything that is not a valid item link. */
    private fun offerDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) deepLinks.offer(intent.dataString)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPlaybackVisible = playbackController.state.value is PlaybackState.Active
        if (platformActions.dispatchMediaKey(event, isPlaybackVisible)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        platformActions.onStop(isChangingConfigurations, isFinishing)
        super.onStop()
    }

    override fun onDestroy() {
        platformActions.onDestroy()
        super.onDestroy()
    }

    private companion object {
        /** False in a fresh process, including one Android starts to restore this activity. */
        var processStarted = false
    }
}
