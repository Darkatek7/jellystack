@file:Suppress("ktlint:standard:backing-property-naming")

package dev.jellystack.players

import dev.jellystack.core.downloads.InMemoryOfflineMediaStore
import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.core.playback.StreamingProgressReporter
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackMediaSourceDto
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.CastSessionSnapshot
import dev.jellystack.players.cast.CastStreamType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    @Test
    fun stopInvalidatesInitialResolveBeforeItCanPreparePlayback() =
        runTest {
            val resolveStarted = CompletableDeferred<Unit>()
            val allowResolve = CompletableDeferred<Unit>()
            val resolver = BlockingInitialPlaybackResolver(resolveStarted, allowResolve)
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            val playJob =
                launch {
                    controller.play(
                        PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                        testEnvironment(),
                    )
                }
            runCurrent()
            resolveStarted.await()

            controller.stop()
            allowResolve.complete(Unit)
            advanceUntilIdle()

            assertEquals(PlaybackState.Stopped, controller.state.value)
            assertNull(controller.currentSession())
            assertEquals(0, engine.prepareCount)
            assertTrue(playJob.isCancelled)
            controller.release()
        }

    @Test
    fun releaseInvalidatesInitialResolveBeforeItCanResurrectPlayback() =
        runTest {
            val resolveStarted = CompletableDeferred<Unit>()
            val allowResolve = CompletableDeferred<Unit>()
            val resolver = BlockingInitialPlaybackResolver(resolveStarted, allowResolve)
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            val playJob =
                launch {
                    controller.play(
                        PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                        testEnvironment(),
                    )
                }
            runCurrent()
            resolveStarted.await()

            controller.release()
            allowResolve.complete(Unit)
            advanceUntilIdle()

            assertEquals(PlaybackState.Stopped, controller.state.value)
            assertNull(controller.currentSession())
            assertEquals(0, engine.prepareCount)
            assertTrue(engine.released)
            assertTrue(playJob.isCancelled)
        }

    @Test
    fun newerPlayWinsWhenOlderInitialResolveCompletesLast() =
        runTest {
            val firstResolveStarted = CompletableDeferred<Unit>()
            val allowFirstResolve = CompletableDeferred<Unit>()
            val resolver = BlockingInitialPlaybackResolver(firstResolveStarted, allowFirstResolve)
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )
            val firstRequest = PlaybackRequest.from(sampleItem("item-1"), sampleDetail(withDirect = true))
            val secondRequest = PlaybackRequest.from(sampleItem("item-2"), sampleDetail(withDirect = true))

            val firstPlay = launch { controller.play(firstRequest, testEnvironment()) }
            runCurrent()
            firstResolveStarted.await()

            controller.play(secondRequest, testEnvironment())
            allowFirstResolve.complete(Unit)
            advanceUntilIdle()

            val state = controller.state.value as PlaybackState.Active
            assertEquals("item-2", state.mediaId)
            assertEquals("https://example.test/item-2.mp4", state.source.url)
            assertEquals(1, engine.prepareCount)
            assertTrue(firstPlay.isCancelled)
            controller.release()
        }

    @Test
    fun selectsDirectH264StreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("h264", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
                // controller.release() cancels controllerScope internally
            }
        }

    @Test
    fun forceRestartIgnoresLocalAndJellyfinResumePositions() =
        runTest {
            val engine = RecordingPlayerEngine()
            val progressStore = InMemoryPlaybackProgressStore().apply { write(PlaybackProgress("item-1", 45_000L)) }
            val controller =
                PlaybackController(
                    progressStore = progressStore,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    playbackPreferencesProvider = PlaybackPreferencesProvider { AppSettings(resumeMode = ResumeMode.RESUME) },
                    scope = TestScope(UnconfinedTestDispatcher()),
                )
            val request =
                PlaybackRequest
                    .from(sampleItem().copy(positionTicks = 20_000L * 10_000L), sampleDetail(withDirect = true))
                    .copy(startPolicy = PlaybackStartPolicy.RESTART)

            try {
                controller.play(request, testEnvironment())

                assertEquals(0L, engine.lastStartPositionMs)
            } finally {
                controller.release()
            }
        }

    @Test
    fun initialPlaybackUsesQualityForCurrentNetwork() =
        runTest {
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    playbackPreferencesProvider =
                        PlaybackPreferencesProvider {
                            AppSettings(wifiStreamingQuality = StreamingQualityPreference.MBPS_4_720P)
                        },
                    playbackNetworkClassifier = PlaybackNetworkClassifier { PlaybackNetworkClass.UNMETERED },
                    scope = TestScope(UnconfinedTestDispatcher()),
                )

            try {
                controller.play(PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = false)), testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(4_000_000, state.stream.maxBitrate)
                assertEquals(4_000_000, engine.lastQuality)
            } finally {
                controller.release()
            }
        }

    @Test
    fun prefersOfflineMediaWhenAvailable() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val offlineStore =
                InMemoryOfflineMediaStore().apply {
                    write(
                        OfflineMedia(
                            mediaId = "item-1",
                            filePath = "/offline/item-1.mp4",
                            mimeType = "video/mp4",
                            checksumSha256 = null,
                            sizeBytes = 1_024L,
                        ),
                    )
                }
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    offlineMediaStore = offlineStore,
                    offlineSourceResolver =
                        object : OfflinePlaybackSourceResolver {
                            override fun resolve(media: OfflineMedia): ResolvedPlaybackSource =
                                ResolvedPlaybackSource(
                                    url = "file://${media.filePath}",
                                    headers = emptyMap(),
                                    mode = PlaybackMode.LOCAL,
                                    mimeType = media.mimeType,
                                    subtitles = emptyList(),
                                    playSessionId = null,
                                    audioStreamIndex = null,
                                    subtitleStreamIndex = null,
                                )
                        },
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackMode.LOCAL, state.stream.mode)
                assertEquals(PlaybackMode.LOCAL, state.source.mode)
                assertEquals("file:///offline/item-1.mp4", state.source.url)
            } finally {
                controller.release()
            }
        }

    @Test
    fun routesCommandsThroughCastSessionWhenConnected() =
        runTest {
            val castManager = FakeCastSessionManager()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val playerEngine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = playerEngine,
                    castSessionManager = castManager,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                controllerScope.advanceUntilIdle()

                val initialState = controller.state.value as PlaybackState.LocalPlayback
                val localDeviceName = initialState.deviceName

                val session = controller.currentSession()
                assertNotNull(session)
                val snapshot =
                    CastSessionSnapshot(
                        mediaId = session.mediaId,
                        title = session.request.metadata?.title,
                        seriesName = session.request.metadata?.seriesName,
                        episodeName = session.request.metadata?.episodeName,
                        artworkUrl = "https://example.com/artwork.jpg",
                        streamUrl = session.source.url,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        isPaused = false,
                    )

                castManager.emitState(CastConnectionState.Connected(deviceName = "Living Room TV", snapshot = snapshot))
                controllerScope.advanceUntilIdle()

                controller.pause()
                controller.resume()
                controller.seekTo(10_000L)
                controllerScope.advanceUntilIdle()

                assertTrue(castManager.commands.contains("pause"))
                assertTrue(castManager.commands.contains("play"))
                assertTrue(castManager.commands.contains("seek:10000"))

                castManager.emitProgress(12_000L)
                controllerScope.advanceUntilIdle()

                val playing = controller.state.value as PlaybackState.CastPlayback
                assertEquals("Living Room TV", playing.castDeviceName)
                assertEquals(12_000L, playing.positionMs)

                val snapshotAfterProgress = controller.currentCastSnapshot()
                assertNotNull(snapshotAfterProgress)
                assertTrue(snapshotAfterProgress.streamUrl.startsWith(session.source.url))
                assertTrue(snapshotAfterProgress.streamUrl.contains("AudioStreamIndex=10"))
                assertEquals(12_000L, snapshotAfterProgress.positionMs)

                castManager.emitState(CastConnectionState.Idle)
                controllerScope.advanceUntilIdle()

                val resumed = controller.state.value as PlaybackState.LocalPlayback
                assertEquals(localDeviceName, resumed.deviceName)
                assertEquals(2, playerEngine.playCount)

                val storedSnapshot = controller.currentCastSnapshot()
                assertNotNull(storedSnapshot)
                assertEquals(12_000L, storedSnapshot.positionMs)
            } finally {
                controller.release()
            }
        }

    @Test
    fun stopCommandRoutesToCastWhenActive() =
        runTest {
            val castManager = FakeCastSessionManager()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = RecordingPlayerEngine(),
                    castSessionManager = castManager,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                controllerScope.advanceUntilIdle()
                val session = controller.currentSession()
                assertNotNull(session)

                val snapshot =
                    CastSessionSnapshot(
                        mediaId = session.mediaId,
                        title = session.request.metadata?.title,
                        seriesName = session.request.metadata?.seriesName,
                        episodeName = session.request.metadata?.episodeName,
                        artworkUrl = null,
                        streamUrl = session.source.url,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        isPaused = false,
                    )

                castManager.emitState(CastConnectionState.Connected(deviceName = "Bedroom TV", snapshot = snapshot))
                controllerScope.advanceUntilIdle()

                controller.stop(saveProgress = true)
                controllerScope.advanceUntilIdle()

                assertTrue(castManager.commands.contains("stop"))
                assertEquals(PlaybackState.Stopped, controller.state.value)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectsDirectHevcStreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true, directCodec = "hevc"))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("hevc", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectsDirectAv1StreamForPlayback() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true, directCodec = "av1"))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals("av1", state.stream.videoCodec?.lowercase())
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectsDirectStreamWhenDirectPlayFlagMissing() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request =
                PlaybackRequest.from(
                    sampleItem(),
                    sampleDetail(
                        withDirect = true,
                        directSupportsDirectPlay = false,
                        directSupportsDirectStream = true,
                    ),
                )

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals("direct-source", state.stream.sourceId)
                assertEquals(PlaybackMode.DIRECT, state.source.mode)
            } finally {
                controller.release()
            }
        }

    @Test
    fun exposesSubtitleTracksAndAllowsSelection() =
        runTest {
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val detail =
                sampleDetail(
                    includeSrt = true,
                    includeVtt = true,
                    includePgs = true,
                    includeSup = true,
                    includeAss = true,
                    includeSsa = true,
                )
            val request = PlaybackRequest.from(sampleItem(), detail)

            try {
                controller.play(request, testEnvironment())
                val state = controller.state.value as PlaybackState.Active

                assertEquals(6, state.stream.subtitleTracks.size)
                assertEquals(
                    setOf(
                        SubtitleFormat.SRT,
                        SubtitleFormat.VTT,
                        SubtitleFormat.PGS,
                        SubtitleFormat.SUP,
                        SubtitleFormat.ASS,
                        SubtitleFormat.SSA,
                    ),
                    state.stream.subtitleTracks
                        .map { it.format }
                        .toSet(),
                )
                val srt = state.stream.subtitleTracks.first { it.format == SubtitleFormat.SRT }
                controller.selectSubtitle(srt.id)
                val updated = controller.state.value as PlaybackState.Active
                assertEquals(srt.id, updated.subtitleTrack?.id)
            } finally {
                controller.release()
            }
        }

    @Test
    fun reportsStreamingProgressOnDirectPlayback() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val reporter = RecordingStreamingProgressReporter()
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    streamingProgressReporter = reporter,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                controller.updateProgress(10_000L)
                controllerScope.testScheduler.advanceUntilIdle()

                val progressByMedia = reporter.progressEvents.map { it.first.mediaId to it.second }
                assertEquals(listOf("item-1" to 10_000L), progressByMedia)

                controller.stop(saveProgress = false)
            } finally {
                controller.release()
            }
        }

    @Test
    fun reportsStreamingCompletionWhenFinishingDirectPlayback() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val reporter = RecordingStreamingProgressReporter()
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    streamingProgressReporter = reporter,
                    scope = controllerScope,
                )
            val detail = sampleDetail(withDirect = true, durationMs = 120_000L)
            val request = PlaybackRequest.from(sampleItem(), detail)

            try {
                controller.play(request, testEnvironment())

                controller.updateProgress(120_000L)
                controllerScope.testScheduler.advanceUntilIdle()

                controller.stop()
                controllerScope.testScheduler.advanceUntilIdle()

                val completedIds = reporter.completedEvents.map { it.mediaId }
                assertEquals(listOf("item-1"), completedIds)
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualityOptionsIncludeAutoAndVariants() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertTrue(state.qualityOptions.isNotEmpty())
                assertEquals(PlaybackQualityOption.AUTO_ID, state.qualityOptions.first().id)
                assertTrue(state.qualityOptions.any { !it.isAuto && it.mode == PlaybackMode.HLS })
            } finally {
                controller.release()
            }
        }

    @Test
    fun initialPlaybackReflectsNegotiatedModeAndSource() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver =
                        QueuePlaybackSourceResolver(
                            resolvedSource(
                                url = "https://example.test/server-selected.m3u8",
                                mode = PlaybackMode.HLS,
                                playSessionId = "play-1",
                                mediaSourceId = "server-selected-source",
                            ),
                        ),
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )

                val state = controller.state.value as PlaybackState.Active
                val auto = state.qualityOptions.single { it.isAuto }
                assertEquals(PlaybackMode.HLS, state.stream.mode)
                assertEquals("server-selected-source", state.stream.sourceId)
                assertEquals(PlaybackMode.HLS, auto.mode)
                assertEquals("server-selected-source", auto.sourceId)
            } finally {
                controller.release()
            }
        }

    @Test
    fun initialNegotiationWithoutTranscodingHidesManualQualityOptions() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver =
                        QueuePlaybackSourceResolver(
                            resolvedSource(
                                url = "https://example.test/server-direct.mp4",
                                mode = PlaybackMode.DIRECT,
                                playSessionId = "play-1",
                                mediaSourceId = "server-direct-source",
                                supportsTranscoding = false,
                            ),
                        ),
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackQualityOption.AUTO_ID, state.selectedQualityId)
                assertEquals(
                    listOf(PlaybackQualityOption.AUTO_ID),
                    state.qualityOptions.map { it.id },
                )
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualityOptionsMapBitrateToResolution() {
        val selection =
            PlaybackStreamSelector().select(
                listOf(
                    mediaSource(
                        id = "quality-source",
                        supportsDirectPlay = true,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                        streams = listOf(videoStream(displayTitle = "2160p", codec = "hevc", bitrate = 120_000_000)),
                        videoBitrate = 120_000_000,
                    ),
                ),
            )

        val options = selection.qualityOptions.filterNot { it.isAuto }
        assertEquals(2160, options.first { it.maxBitrate == 120_000_000 }.maxHeight)
        assertEquals(1440, options.first { it.maxBitrate == 10_000_000 }.maxHeight)
        assertEquals(1080, options.first { it.maxBitrate == 6_000_000 }.maxHeight)
        assertEquals(720, options.first { it.maxBitrate == 1_500_000 }.maxHeight)
        assertEquals(480, options.first { it.maxBitrate == 720_000 }.maxHeight)
        assertEquals(360, options.first { it.maxBitrate == 420_000 }.maxHeight)
        assertEquals("6.0 Mbps · 1080p", options.first { it.maxBitrate == 6_000_000 }.label)
        assertEquals("420 Kbps · 360p", options.first { it.maxBitrate == 420_000 }.label)
    }

    @Test
    fun qualityOptionsUseVideoStreamBitrateWhenSourceBitrateIsMissing() {
        val source =
            mediaSource(
                id = "stream-bitrate-source",
                supportsDirectPlay = true,
                supportsDirectStream = true,
                supportsTranscoding = true,
                streams = listOf(videoStream(displayTitle = "1080p", codec = "h264", bitrate = 5_000_000)),
                videoBitrate = null,
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertEquals(
            listOf(6_000_000, 4_000_000, 3_000_000, 1_500_000, 720_000, 420_000),
            selection.qualityOptions.filter { it.mode == PlaybackMode.HLS }.map { it.maxBitrate },
        )
    }

    @Test
    fun qualityOptionsUseFullPresetLadderWhenBitrateMetadataIsMissing() {
        val source =
            mediaSource(
                id = "unknown-bitrate-source",
                supportsDirectPlay = true,
                supportsDirectStream = true,
                supportsTranscoding = true,
                streams = listOf(videoStream(displayTitle = "1080p", codec = "h264")),
                videoBitrate = null,
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertEquals(
            listOf(
                120_000_000,
                80_000_000,
                60_000_000,
                40_000_000,
                20_000_000,
                15_000_000,
                10_000_000,
                8_000_000,
                6_000_000,
                4_000_000,
                3_000_000,
                1_500_000,
                720_000,
                420_000,
            ),
            selection.qualityOptions.filter { it.mode == PlaybackMode.HLS }.map { it.maxBitrate },
        )
    }

    @Test
    fun qualityOptionsDoNotOfferAdaptiveVariantsWhenTranscodingIsUnavailable() {
        val source =
            mediaSource(
                id = "direct-only-source",
                supportsDirectPlay = true,
                supportsDirectStream = true,
                supportsTranscoding = false,
                streams = listOf(videoStream(displayTitle = "1080p", codec = "h264", bitrate = 5_000_000)),
                videoBitrate = null,
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertTrue(selection.qualityOptions.none { it.mode == PlaybackMode.HLS })
    }

    @Test
    fun selectingHlsQualityUpdatesSelection() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                val initial = controller.state.value as PlaybackState.Active
                val hlsOption = initial.qualityOptions.first { it.mode == PlaybackMode.HLS }

                controller.selectQuality(hlsOption.id)

                val updated = controller.state.value as PlaybackState.Active
                assertEquals(hlsOption.id, updated.selectedQualityId)
                assertEquals(PlaybackMode.HLS, updated.stream.mode)
                assertEquals(hlsOption.maxBitrate, updated.stream.maxBitrate)
                assertEquals(request.metadata, updated.metadata)
            } finally {
                controller.release()
            }
        }

    @Test
    fun returningFromManualQualityToAutoRepreparesDirectSourceWithoutCrash() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine = RecordingPlayerEngine()
            val resolver =
                QueuePlaybackSourceResolver(
                    resolvedSource(
                        url = "https://example.test/direct.mp4",
                        mode = PlaybackMode.DIRECT,
                        playSessionId = "play-1",
                        mediaSourceId = "direct-source",
                    ),
                    resolvedSource(
                        url = "https://example.test/manual.m3u8",
                        mode = PlaybackMode.HLS,
                        playSessionId = "play-2",
                        mediaSourceId = "hls-source",
                    ),
                    resolvedSource(
                        url = "https://example.test/direct.mp4",
                        mode = PlaybackMode.DIRECT,
                        playSessionId = "play-3",
                        mediaSourceId = "direct-source",
                    ),
                )
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()
                controller.selectQuality(PlaybackQualityOption.AUTO_ID)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackQualityOption.AUTO_ID, state.selectedQualityId)
                assertEquals(PlaybackMode.DIRECT, state.stream.mode)
                assertEquals(3, engine.prepareCount)
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualitySwitchFailureBecomesRetryablePlaybackError() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            var calls = 0
            val resolver =
                PlaybackSourceResolver { _, selection, _, _, _ ->
                    calls += 1
                    if (calls == 3) error("Auto negotiation failed")
                    resolvedSource(
                        url =
                            if (selection.mode == PlaybackMode.HLS) {
                                "https://example.test/manual.m3u8"
                            } else {
                                "https://example.test/direct.mp4"
                            },
                        mode = selection.mode,
                        playSessionId = "play-$calls",
                        mediaSourceId = selection.sourceId,
                    )
                }
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }
                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                controller.selectQuality(PlaybackQualityOption.AUTO_ID)
                controllerScope.advanceUntilIdle()

                val error = controller.state.value as PlaybackState.PlaybackError
                assertEquals("Auto negotiation failed", error.message)
                assertTrue(error.canRetry)
            } finally {
                controller.release()
            }
        }

    @Test
    fun newerQualitySelectionCancelsOlderPendingSwitch() =
        runTest {
            val firstSwitchStarted = CompletableDeferred<Unit>()
            val releaseFirstSwitch = CompletableDeferred<Unit>()
            val resolver = DelayingQualityResolver(firstSwitchStarted, releaseFirstSwitch)
            val controllerScope = TestScope(StandardTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val options =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .filterNot { it.isAuto }

                controller.selectQuality(options[0].id)
                controllerScope.runCurrent()
                firstSwitchStarted.await()
                controller.selectQuality(options[1].id)
                releaseFirstSwitch.complete(Unit)
                controllerScope.advanceUntilIdle()

                assertEquals(
                    options[1].id,
                    (controller.state.value as PlaybackState.Active).selectedQualityId,
                )
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualitySwitchPreservesPositionPauseAudioAndSubtitle() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        sampleDetail(includeJapaneseAudio = true, includeVtt = true),
                    ),
                    testEnvironment(),
                )
                controller.selectAudioTrack("8")
                controller.selectSubtitle("3")
                controller.updateProgress(12_345L)
                controller.pause()
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(12_345L, state.positionMs)
                assertTrue(state.isPaused)
                assertEquals("8", state.audioTrack?.id)
                assertEquals("3", state.subtitleTrack?.id)
                assertEquals(12_345L, engine.lastStartPositionMs)
                assertEquals("8", engine.lastAudioTrack?.id)
                assertEquals("3", engine.lastSubtitleTrack?.id)
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualitySwitchRestartsWhenProgressPassedTheMediaDuration() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(durationMs = 90_000L)),
                    testEnvironment(),
                )
                controller.updateProgress(90_100L)
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(0L, state.positionMs)
                assertEquals(0L, engine.lastStartPositionMs)
                assertEquals("0", state.source.url.queryParameter("StartTimeTicks"))
            } finally {
                controller.release()
            }
        }

    @Test
    fun fallbackHlsKeepsItsServerTimelineAtZeroDuringQualitySwitch() =
        runTest {
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver =
                        QueuePlaybackSourceResolver(
                            resolvedSource(
                                url = "https://example.test/direct.mp4",
                                mode = PlaybackMode.DIRECT,
                                playSessionId = "play-direct",
                                mediaSourceId = "direct-source",
                            ),
                            resolvedSource(
                                url = "https://example.test/master.m3u8?StartTimeTicks=0",
                                mode = PlaybackMode.HLS,
                                playSessionId = "play-fallback",
                                mediaSourceId = "direct-source",
                            ).copy(isFallbackHls = true),
                        ),
                    playerEngine = engine,
                    scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                controller.updateProgress(12_345L)
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)

                val state = controller.state.value as PlaybackState.Active
                assertEquals(12_345L, state.positionMs)
                assertEquals(12_345L, engine.lastStartPositionMs)
                assertEquals("0", state.source.url.queryParameter("StartTimeTicks"))
            } finally {
                controller.release()
            }
        }

    @Test
    fun pendingQualitySwitchUsesLatestProgressPauseAudioAndSubtitleChanges() =
        runTest {
            val switchStarted = CompletableDeferred<Unit>()
            val allowSwitch = CompletableDeferred<Unit>()
            val resolver = DelayingQualityResolver(switchStarted, allowSwitch, hlsFragment = "#playback-fragment")
            val engine = RecordingPlayerEngine()
            val controllerScope = TestScope(StandardTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        sampleDetail(includeJapaneseAudio = true, includeVtt = true),
                    ),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }
                controller.selectQuality(manual.id)
                controllerScope.runCurrent()
                switchStarted.await()

                controller.pause()
                controller.updateProgress(23_456L)
                controller.selectAudioTrack("8")
                controller.selectSubtitle("3")
                allowSwitch.complete(Unit)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(23_456L, state.positionMs)
                assertTrue(state.isPaused)
                assertEquals("8", state.audioTrack?.id)
                assertEquals("3", state.subtitleTrack?.id)
                assertEquals(23_456L, engine.lastStartPositionMs)
                assertEquals("8", engine.lastAudioTrack?.id)
                assertEquals("3", engine.lastSubtitleTrack?.id)
                assertEquals("8", state.source.url.queryParameter("AudioStreamIndex"))
                assertEquals("234560000", state.source.url.queryParameter("StartTimeTicks"))
                assertTrue(state.source.url.endsWith("#playback-fragment"))
            } finally {
                controller.release()
            }
        }

    @Test
    fun autoQualityReflectsNegotiatedModeAndSource() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val resolver =
                QueuePlaybackSourceResolver(
                    resolvedSource(
                        url = "https://example.test/direct.mp4",
                        mode = PlaybackMode.DIRECT,
                        playSessionId = "play-1",
                        mediaSourceId = "direct-source",
                    ),
                    resolvedSource(
                        url = "https://example.test/manual.m3u8",
                        mode = PlaybackMode.HLS,
                        playSessionId = "play-2",
                        mediaSourceId = "hls-source",
                    ),
                    resolvedSource(
                        url = "https://example.test/server-selected.m3u8",
                        mode = PlaybackMode.HLS,
                        playSessionId = "play-3",
                        mediaSourceId = "server-selected-source",
                    ),
                )
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }
                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                controller.selectQuality(PlaybackQualityOption.AUTO_ID)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                val auto = state.qualityOptions.single { it.isAuto }
                assertEquals(PlaybackMode.HLS, state.stream.mode)
                assertEquals("server-selected-source", state.stream.sourceId)
                assertEquals(PlaybackMode.HLS, auto.mode)
                assertEquals("server-selected-source", auto.sourceId)
            } finally {
                controller.release()
            }
        }

    @Test
    fun negotiatedSourceWithoutTranscodingHidesManualQualityOptions() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val resolver =
                QueuePlaybackSourceResolver(
                    resolvedSource(
                        url = "https://example.test/direct.mp4",
                        mode = PlaybackMode.DIRECT,
                        playSessionId = "play-1",
                        mediaSourceId = "direct-source",
                    ),
                    resolvedSource(
                        url = "https://example.test/server-direct.mp4",
                        mode = PlaybackMode.DIRECT,
                        playSessionId = "play-2",
                        mediaSourceId = "server-direct-source",
                        supportsTranscoding = false,
                    ),
                )
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackQualityOption.AUTO_ID, state.selectedQualityId)
                assertEquals(
                    listOf(PlaybackQualityOption.AUTO_ID),
                    state.qualityOptions.map { it.id },
                )
            } finally {
                controller.release()
            }
        }

    @Test
    fun omittedTranscodingFlagWithServerUrlPreservesSelectedManualQualityOptions() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver =
                        JellyfinPlaybackSourceResolver(
                            playbackInfoService =
                                JellyfinPlaybackInfoService { _, itemId, _, request ->
                                    JellyfinPlaybackInfoResponseDto(
                                        playSessionId = "play-${request.maxStreamingBitrate ?: "auto"}",
                                        mediaSources =
                                            listOf(
                                                JellyfinPlaybackMediaSourceDto(
                                                    id = request.mediaSourceId,
                                                    transcodingUrl = "/Videos/$itemId/master.m3u8?VideoCodec=h264",
                                                    transcodingContainer = "ts",
                                                    transcodingSubProtocol = "hls",
                                                ),
                                            ),
                                    )
                                },
                        ),
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .last { !it.isAuto }

                controller.selectQuality(manual.id)
                controllerScope.advanceUntilIdle()

                val state = controller.state.value as PlaybackState.Active
                assertEquals(manual.id, state.selectedQualityId)
                assertTrue(state.qualityOptions.any { it.id == manual.id })
                assertTrue(state.qualityOptions.any { it.isAuto })
            } finally {
                controller.release()
            }
        }

    @Test
    fun stopCancelsPendingQualitySwitch() =
        runTest {
            val firstSwitchStarted = CompletableDeferred<Unit>()
            val releaseFirstSwitch = CompletableDeferred<Unit>()
            val resolver = DelayingQualityResolver(firstSwitchStarted, releaseFirstSwitch)
            val controllerScope = TestScope(StandardTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)),
                    testEnvironment(),
                )
                val manual =
                    (controller.state.value as PlaybackState.Active)
                        .qualityOptions
                        .first { !it.isAuto }
                controller.selectQuality(manual.id)
                controllerScope.runCurrent()
                firstSwitchStarted.await()

                controller.stop()
                releaseFirstSwitch.complete(Unit)
                controllerScope.advanceUntilIdle()

                assertEquals(PlaybackState.Stopped, controller.state.value)
                assertNull(controller.currentSession())
            } finally {
                controller.release()
            }
        }

    @Test
    fun changingHlsQualityPreservesSelectedAudioAndPlaySession() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver =
                        JellyfinPlaybackSourceResolver(
                            playbackInfoService =
                                JellyfinPlaybackInfoService { _, itemId, _, playbackInfoRequest ->
                                    JellyfinPlaybackInfoResponseDto(
                                        playSessionId = "controller-play-session",
                                        mediaSources =
                                            listOf(
                                                JellyfinPlaybackMediaSourceDto(
                                                    id = playbackInfoRequest.mediaSourceId,
                                                    container = "ts",
                                                    supportsTranscoding = true,
                                                    transcodingUrl =
                                                        "/Videos/$itemId/master.m3u8" +
                                                            "?PlaySessionId=controller-play-session" +
                                                            "&AudioStreamIndex=${playbackInfoRequest.audioStreamIndex}",
                                                    transcodingContainer = "ts",
                                                    transcodingSubProtocol = "hls",
                                                ),
                                            ),
                                    )
                                },
                        ),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request =
                PlaybackRequest.from(
                    sampleItem(),
                    sampleDetail(includeJapaneseAudio = true),
                )

            try {
                controller.play(request, testEnvironment())
                val initialSession = assertNotNull(controller.currentSession())
                val initialPlaySessionId = assertNotNull(initialSession.source.playSessionId)
                controller.selectAudioTrack("8")
                val manualQuality = initialSession.qualityOptions.last { it.mode == PlaybackMode.HLS }

                controller.selectQuality(manualQuality.id)
                controllerScope.advanceUntilIdle()

                val preparedSource = assertNotNull(engine.lastPrepared)
                val updatedSession = assertNotNull(controller.currentSession())
                assertEquals("8", updatedSession.audioTrack?.id)
                assertEquals("8", preparedSource.url.queryParameter("AudioStreamIndex"))
                assertEquals(initialPlaySessionId, preparedSource.url.queryParameter("PlaySessionId"))
                assertEquals(initialPlaySessionId, preparedSource.playSessionId)
                assertEquals(initialPlaySessionId, updatedSession.source.playSessionId)
            } finally {
                controller.release()
            }
        }

    @Test
    fun selectingAudioOnHlsReResolvesTheSourceAndPreservesPlaybackState() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine = RecordingPlayerEngine()
            val resolver = RecordingAudioSwitchResolver()
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        sampleDetail(includeJapaneseAudio = true, includeVtt = true),
                    ),
                    testEnvironment(),
                )
                controller.selectSubtitle("3")
                controller.updateProgress(23_456L)
                controller.pause()

                controller.selectAudioTrack("8")
                controllerScope.advanceUntilIdle()

                val session = assertNotNull(controller.currentSession())
                assertEquals(2, resolver.requests.size)
                assertEquals(8, resolver.requests.last().audioStreamIndex)
                assertEquals(2, engine.prepareCount)
                assertEquals("8", session.audioTrack?.id)
                assertEquals(8, session.source.audioStreamIndex)
                assertEquals(23_456L, session.positionMs)
                assertEquals("3", session.subtitleTrack?.id)
                assertTrue(session.isPaused)
                assertEquals(23_456L, engine.lastStartPositionMs)
                assertEquals("8", engine.lastAudioTrack?.id)
                assertEquals("3", engine.lastSubtitleTrack?.id)
            } finally {
                controller.release()
            }
        }

    @Test
    fun failedHlsAudioSwitchKeepsTheActuallyPlayingTrackSelected() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    playbackSourceResolver = FailingAudioSwitchResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        sampleDetail(includeJapaneseAudio = true),
                    ),
                    testEnvironment(),
                )
                val original = assertNotNull(controller.currentSession()).audioTrack

                controller.selectAudioTrack("8")
                controllerScope.advanceUntilIdle()

                val session = assertNotNull(controller.currentSession())
                assertEquals(original?.id, session.audioTrack?.id)
                assertEquals(1, engine.prepareCount)
            } finally {
                controller.release()
            }
        }

    @Test
    fun newerHlsAudioSelectionCancelsAnOlderPendingSwitch() =
        runTest {
            val firstSwitchStarted = CompletableDeferred<Unit>()
            val releaseFirstSwitch = CompletableDeferred<Unit>()
            val resolver =
                DelayingAudioSwitchResolver(
                    firstSwitchStarted = firstSwitchStarted,
                    releaseFirstSwitch = releaseFirstSwitch,
                )
            val controllerScope = TestScope(StandardTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    scope = controllerScope,
                )
            val baseDetail = sampleDetail(includeJapaneseAudio = true)
            val detail =
                baseDetail.copy(
                    mediaSources =
                        baseDetail.mediaSources.map { source ->
                            source.copy(
                                streams =
                                    source.streams +
                                        audioStream(
                                            index = 9,
                                            language = "deu",
                                            isDefault = false,
                                        ),
                            )
                        },
                )

            try {
                controller.play(PlaybackRequest.from(sampleItem(), detail), testEnvironment())
                controller.selectAudioTrack("8")
                controllerScope.runCurrent()
                firstSwitchStarted.await()

                controller.selectAudioTrack("9")
                releaseFirstSwitch.complete(Unit)
                controllerScope.advanceUntilIdle()

                val session = assertNotNull(controller.currentSession())
                assertEquals("9", session.audioTrack?.id)
                assertEquals(9, session.source.audioStreamIndex)
                assertEquals(listOf<Int?>(1, 8, 9), resolver.requestedAudioIndices)
            } finally {
                controller.release()
            }
        }

    @Test
    fun preferredEnglishAudioIsSentInTheInitialPlaybackInfoRequest() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val resolver = RecordingAudioSwitchResolver()
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = RecordingPlayerEngine(),
                    playbackPreferencesProvider =
                        PlaybackPreferencesProvider {
                            AppSettings(preferredAudioLanguage = "eng")
                        },
                    scope = controllerScope,
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        sampleDetail(
                            includeJapaneseAudio = true,
                            englishIsDefault = false,
                        ),
                    ),
                    testEnvironment(),
                )

                assertEquals(8, resolver.requests.single().audioStreamIndex)
                assertEquals("eng", controller.currentSession()?.audioTrack?.language)
            } finally {
                controller.release()
            }
        }

    @Test
    fun pendingDirectAudioSelectionUpdatesStateOnlyAfterEngineConfirmation() =
        runTest {
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val engine =
                RecordingPlayerEngine().apply {
                    audioSelectionResult = AudioTrackSelectionResult.PENDING
                }
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val baseDetail = sampleDetail(withDirect = true)
            val detail =
                baseDetail.copy(
                    mediaSources =
                        baseDetail.mediaSources.map { source ->
                            if (source.id == "direct-source") {
                                source.copy(
                                    streams =
                                        source.streams +
                                            audioStream(
                                                index = 8,
                                                language = "jpn",
                                                isDefault = false,
                                            ),
                                )
                            } else {
                                source
                            }
                        },
                )

            try {
                controller.play(
                    PlaybackRequest.from(
                        sampleItem(),
                        detail,
                    ),
                    testEnvironment(),
                )
                val originalTrackId = controller.currentSession()?.audioTrack?.id

                controller.selectAudioTrack("8")
                assertEquals(originalTrackId, controller.currentSession()?.audioTrack?.id)

                engine.emitEvent(PlayerEvent.AudioTrackSelectionApplied("8"))
                controllerScope.advanceUntilIdle()

                assertEquals("8", controller.currentSession()?.audioTrack?.id)
                assertEquals(8, controller.currentSession()?.source?.audioStreamIndex)
            } finally {
                controller.release()
            }
        }

    @Test
    fun qualityOptionsDoNotExposeMediaTitleAsDirectQuality() {
        val episodeTitle = "MEINE WIEDERGEBURT ALS SCHLEIM IN EINER ANDEREN WELT - E01"
        val source =
            mediaSource(
                id = "source-with-title",
                supportsDirectPlay = true,
                supportsDirectStream = true,
                supportsTranscoding = true,
                streams =
                    listOf(
                        videoStream(
                            displayTitle = episodeTitle,
                            codec = "h264",
                            bitrate = 1_500_000,
                        ),
                    ),
                videoBitrate = 1_500_000,
            )

        val selection = PlaybackStreamSelector().select(listOf(source))

        assertTrue(selection.qualityOptions.none { !it.isAuto && it.mode == PlaybackMode.DIRECT })
        assertTrue(selection.qualityOptions.none { it.label.contains(episodeTitle) })
    }

    @Test
    fun persistsProgressAndRestoresOnNextSession() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val request = PlaybackRequest.from(sampleItem(), sampleDetail())
            assertEquals(90_000L, ticksToMillis(request.durationTicks))

            val engineOne = NoopPlayerEngine()
            val firstScope = TestScope(UnconfinedTestDispatcher())
            val first =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engineOne,
                    scope = firstScope,
                )
            try {
                first.play(request, testEnvironment())
                first.updateProgress(45_000L)
                val currentSession = first.currentSession()
                assertNotNull(currentSession)
                assertEquals(45_000L, currentSession.positionMs)
                assertEquals(90_000L, currentSession.durationMs)
                val afterUpdate = store.read(request.mediaId)
                assertNotNull(afterUpdate)
                assertEquals(45_000L, afterUpdate.positionMs)
                first.stop()
                val afterFirstStop = store.read(request.mediaId)
                assertNotNull(afterFirstStop)
                assertEquals(45_000L, afterFirstStop.positionMs)
            } finally {
                first.release()
            }

            val secondScope = TestScope(UnconfinedTestDispatcher())
            val second =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = NoopPlayerEngine(),
                    scope = secondScope,
                )
            try {
                second.play(request, testEnvironment())
                val restored = second.state.value as PlaybackState.Active
                assertEquals(45_000L, restored.positionMs)

                second.updateProgress(100_000L)
                second.stop()
            } finally {
                second.release()
            }

            val cleared = store.read(request.mediaId)
            assertNull(cleared)
        }

    @Test
    fun playbackRestartsWhenSavedProgressPassedTheMediaDuration() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            store.write(PlaybackProgress(mediaId = "item-1", positionMs = 90_100L))
            val engine = RecordingPlayerEngine()
            val controller =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
                )

            try {
                controller.play(
                    PlaybackRequest.from(sampleItem(), sampleDetail(durationMs = 90_000L)),
                    testEnvironment(),
                )

                val state = controller.state.value as PlaybackState.Active
                assertEquals(0L, state.positionMs)
                assertEquals(0L, engine.lastStartPositionMs)
            } finally {
                controller.release()
            }
        }

    @Test
    fun clearsProgressWhenCompleted() =
        runTest {
            val store = InMemoryPlaybackProgressStore()
            val detail = sampleDetail(durationMs = 120_000L)
            val request = PlaybackRequest.from(sampleItem(), detail)
            val engine = NoopPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = store,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )

            try {
                controller.play(request, testEnvironment())
                controller.updateProgress(120_000L)
                controller.stop()

                assertNull(store.read(request.mediaId))
            } finally {
                controller.release()
            }
        }

    @Test
    fun castSnapshotIncludesContentTypeAndSubtitles() =
        runTest {
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val resolver =
                PlaybackSourceResolver { _, selection, environment, _, options ->
                    ResolvedPlaybackSource(
                        url = "${environment.baseUrl}/videos/${request.mediaId}/${selection.sourceId}",
                        headers = emptyMap(),
                        mode = selection.mode,
                        mimeType = "video/mp4",
                        subtitles =
                            listOf(
                                ResolvedSubtitle(
                                    trackId = "sub-1",
                                    url = "${environment.baseUrl}/subs/${request.mediaId}.vtt",
                                    mimeType = "text/vtt",
                                    isForced = false,
                                    language = "en",
                                    label = "English",
                                ),
                            ),
                        playSessionId = options.playSessionId ?: "session-${request.mediaId}",
                        audioStreamIndex = options.audioStreamIndex ?: selection.defaultAudioTrack()?.streamIndex,
                        subtitleStreamIndex = selection.defaultSubtitleTrack()?.streamIndex,
                    )
                }
            val controller =
                PlaybackController(
                    playbackSourceResolver = resolver,
                    playerEngine = NoopPlayerEngine(),
                    scope = controllerScope,
                )

            try {
                controller.play(request, testEnvironment())
                val snapshot = controller.currentCastSnapshot() ?: error("Missing cast snapshot")
                assertEquals("video/mp4", snapshot.contentType)
                assertEquals(CastStreamType.BUFFERED, snapshot.streamType)
                assertEquals(1, snapshot.subtitleTracks.size)
                assertEquals("sub-1", snapshot.subtitleTracks.first().id)
            } finally {
                controller.release()
            }
        }

    @Test
    fun subtitleSelectionRoutesToCastManagerWhenConnected() =
        runTest {
            val castManager = FakeCastSessionManager()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = RecordingPlayerEngine(),
                    castSessionManager = castManager,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(includeVtt = true))

            try {
                controller.play(request, testEnvironment())
                controllerScope.advanceUntilIdle()
                val session = controller.currentSession()
                assertNotNull(session)
                val subtitle = session.stream.subtitleTracks.firstOrNull()
                assertNotNull(subtitle)
                val snapshot =
                    CastSessionSnapshot(
                        mediaId = session.mediaId,
                        title = session.request.metadata?.title,
                        seriesName = session.request.metadata?.seriesName,
                        episodeName = session.request.metadata?.episodeName,
                        artworkUrl = null,
                        streamUrl = session.source.url,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        isPaused = false,
                    )
                castManager.emitState(CastConnectionState.Connected(deviceName = "Living Room TV", snapshot = snapshot))
                controllerScope.advanceUntilIdle()

                controller.selectSubtitle(subtitle.id)
                controllerScope.advanceUntilIdle()

                assertTrue(castManager.commands.contains("subtitle:${subtitle.id}"))
            } finally {
                controller.release()
            }
        }

    @Test
    fun emitsConnectingAndCastStatesDuringHandoff() =
        runTest {
            val castManager = FakeCastSessionManager()
            val playerEngine = RecordingPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = playerEngine,
                    castSessionManager = castManager,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                controllerScope.advanceUntilIdle()
                assertTrue(controller.state.value is PlaybackState.LocalPlayback)

                castManager.emitState(CastConnectionState.Connecting(deviceName = "Living Room TV"))
                controllerScope.advanceUntilIdle()
                val connecting = controller.state.value as PlaybackState.CastConnecting
                assertEquals(request.metadata, connecting.metadata)
                val session = controller.currentSession() ?: error("Missing session")
                castManager.emitState(
                    CastConnectionState.Connected(
                        deviceName = "Living Room TV",
                        snapshot =
                            CastSessionSnapshot(
                                mediaId = session.mediaId,
                                title = session.request.metadata?.title,
                                seriesName = session.request.metadata?.seriesName,
                                episodeName = session.request.metadata?.episodeName,
                                artworkUrl = null,
                                streamUrl = session.source.url,
                                positionMs = session.positionMs,
                                durationMs = session.durationMs,
                                isPaused = false,
                            ),
                    ),
                )
                controllerScope.advanceUntilIdle()

                val castPlayback = controller.state.value as PlaybackState.CastPlayback
                assertEquals(request.metadata, castPlayback.metadata)
                assertEquals(1, playerEngine.pauseCount)
            } finally {
                controller.release()
            }
        }

    @Test
    fun unexpectedCastDisconnectRecoversLocallyAtLastRemotePosition() =
        runTest {
            val castManager = FakeCastSessionManager()
            val playerEngine = RecordingPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = playerEngine,
                    castSessionManager = castManager,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                controllerScope.advanceUntilIdle()
                val session = controller.currentSession() ?: error("Missing session")
                castManager.emitState(
                    CastConnectionState.Connected(
                        deviceName = "Bedroom TV",
                        snapshot =
                            CastSessionSnapshot(
                                mediaId = session.mediaId,
                                title = session.request.metadata?.title,
                                seriesName = session.request.metadata?.seriesName,
                                episodeName = session.request.metadata?.episodeName,
                                artworkUrl = null,
                                streamUrl = session.source.url,
                                positionMs = 9_000L,
                                durationMs = session.durationMs,
                                isPaused = false,
                            ),
                    ),
                )
                castManager.emitProgress(12_000L)
                controllerScope.advanceUntilIdle()

                castManager.emitState(CastConnectionState.Idle)
                val recovering = controller.state.value as PlaybackState.RecoveringPlayback
                assertEquals(request.metadata, recovering.metadata)
                controllerScope.advanceUntilIdle()

                val finalState = controller.state.value as PlaybackState.LocalPlayback
                assertEquals(12_000L, finalState.positionMs)
                assertEquals(request.metadata, finalState.metadata)
                assertEquals(2, playerEngine.playCount)
                assertTrue(playerEngine.seekPositions.contains(12_000L))
            } finally {
                controller.release()
            }
        }

    @Test
    fun duplicateConnectedCallbacksStayIdempotent() =
        runTest {
            val castManager = FakeCastSessionManager()
            val playerEngine = RecordingPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = InMemoryPlaybackProgressStore(),
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = playerEngine,
                    castSessionManager = castManager,
                    scope = controllerScope,
                )

            try {
                controller.play(PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true)), testEnvironment())
                controllerScope.advanceUntilIdle()
                val session = controller.currentSession() ?: error("Missing session")
                val snapshot =
                    CastSessionSnapshot(
                        mediaId = session.mediaId,
                        title = session.request.metadata?.title,
                        seriesName = session.request.metadata?.seriesName,
                        episodeName = session.request.metadata?.episodeName,
                        artworkUrl = null,
                        streamUrl = session.source.url,
                        positionMs = session.positionMs,
                        durationMs = session.durationMs,
                        isPaused = false,
                    )

                castManager.emitState(CastConnectionState.Connected(deviceName = "Office TV", snapshot = snapshot))
                castManager.emitState(CastConnectionState.Connected(deviceName = "Office TV", snapshot = snapshot))
                controllerScope.advanceUntilIdle()

                val currentState = controller.state.value as PlaybackState.CastPlayback
                assertEquals("Office TV", currentState.castDeviceName)
                assertEquals(1, playerEngine.pauseCount)
            } finally {
                controller.release()
            }
        }

    @Test
    fun playbackRequestCarriesEpisodeMetadataIntoActiveState() =
        runTest {
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    scope = TestScope(UnconfinedTestDispatcher()),
                )
            val item =
                sampleItem().copy(
                    type = "Episode",
                    seriesId = "series-1",
                    seriesName = "Sample Series",
                    episodeTitle = "A New Start",
                    parentIndexNumber = 2,
                    indexNumber = 3,
                )
            val request = PlaybackRequest.from(item, sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())

                val state = controller.state.value as PlaybackState.Active
                assertEquals("Sample Series", state.metadata?.seriesName)
                assertEquals("A New Start", state.metadata?.episodeName)
                assertEquals(2, state.metadata?.seasonNumber)
                assertEquals(3, state.metadata?.episodeNumber)

                controller.pause()
                assertEquals(request.metadata, (controller.state.value as PlaybackState.Active).metadata)
                controller.seekTo(12_000L)
                assertEquals(request.metadata, (controller.state.value as PlaybackState.Active).metadata)
                controller.resume()
                assertEquals(request.metadata, (controller.state.value as PlaybackState.Active).metadata)
            } finally {
                controller.release()
            }
        }

    @Test
    fun exposesPreparingStateUntilEngineIsReady() =
        runTest {
            val engine = BlockingPlayerEngine()
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                val playJob = launch { controller.play(request, testEnvironment()) }
                runCurrent()

                val preparing = controller.state.value as PlaybackState.Preparing
                assertEquals(request.mediaId, preparing.mediaId)
                assertEquals(request.metadata, preparing.metadata)

                engine.allowPrepare.complete(Unit)
                playJob.join()
                assertTrue(controller.state.value is PlaybackState.Active)
            } finally {
                controller.release()
            }
        }

    @Test
    fun playerFailureShowsRetryableErrorAndRetryRestoresProgress() =
        runTest {
            val engine = RecordingPlayerEngine()
            val progressStore = InMemoryPlaybackProgressStore()
            val controllerScope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val controller =
                PlaybackController(
                    progressStore = progressStore,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request = PlaybackRequest.from(sampleItem(), sampleDetail(withDirect = true))

            try {
                controller.play(request, testEnvironment())
                engine.emitPosition(42_000L)
                engine.emitEvent(PlayerEvent.Error(IllegalStateException("Decoder failed")))
                controllerScope.advanceUntilIdle()

                val error = controller.state.value as PlaybackState.PlaybackError
                assertEquals(request.mediaId, error.mediaId)
                assertEquals(request.metadata, error.metadata)
                assertEquals("Decoder failed", error.message)
                assertTrue(error.canRetry)
                assertEquals(42_000L, progressStore.read(request.mediaId)?.positionMs)

                controller.retry()
                controllerScope.advanceUntilIdle()

                assertTrue(controller.state.value is PlaybackState.Active)
                assertEquals(42_000L, engine.lastStartPositionMs)
                assertEquals(2, engine.playCount)

                controller.stop()
                controller.retry()
                controllerScope.advanceUntilIdle()
                assertEquals(PlaybackState.Stopped, controller.state.value)
            } finally {
                controller.release()
            }
        }

    @Test
    fun mediaKindAndPhaseSurvivePrepareBufferAndCompletion() =
        runTest {
            val engine = RecordingPlayerEngine()
            val progressStore = InMemoryPlaybackProgressStore()
            val controllerScope = TestScope(UnconfinedTestDispatcher())
            val controller =
                PlaybackController(
                    progressStore = progressStore,
                    playbackSourceResolver = TestPlaybackSourceResolver(),
                    playerEngine = engine,
                    scope = controllerScope,
                )
            val request =
                PlaybackRequest.from(
                    sampleItem().copy(type = "Audio", mediaType = "Audio"),
                    sampleDetail(withDirect = true),
                )

            try {
                controller.play(request, testEnvironment())
                assertEquals(PlaybackPhase.Buffering, (controller.state.value as PlaybackState.Active).phase)

                engine.emitEvent(PlayerEvent.Ready)
                controllerScope.advanceUntilIdle()
                val ready = controller.state.value as PlaybackState.LocalPlayback
                assertEquals(PlaybackMediaKind.AUDIO, ready.mediaKind)
                assertEquals(PlaybackPhase.Ready, ready.phase)

                engine.emitEvent(PlayerEvent.Buffering)
                controllerScope.advanceUntilIdle()
                controller.pause()
                controller.seekTo(12_000L)
                assertEquals(PlaybackPhase.Buffering, (controller.state.value as PlaybackState.Active).phase)

                engine.emitEvent(PlayerEvent.Completed)
                controllerScope.advanceUntilIdle()
                val ended = controller.state.value as PlaybackState.Active
                assertEquals(PlaybackPhase.Ended, ended.phase)
                assertTrue(ended.isPaused)

                controller.resume()
                assertEquals(0L, engine.seekPositions.last())
                assertEquals(PlaybackPhase.Buffering, (controller.state.value as PlaybackState.Active).phase)
                engine.emitEvent(PlayerEvent.Completed)
                controllerScope.advanceUntilIdle()
                controller.stop()
                assertNull(progressStore.read(request.mediaId))
            } finally {
                controller.release()
            }
        }
}

private fun testEnvironment(): JellyfinEnvironment =
    JellyfinEnvironment(
        serverKey = "server-1",
        baseUrl = "https://demo.jellyfin.org",
        accessToken = "dummy-token",
        userId = "user",
        deviceId = "device-id",
        deviceName = "UnitTest",
    )

private class TestPlaybackSourceResolver : PlaybackSourceResolver {
    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource =
        ResolvedPlaybackSource(
            url = "${environment.baseUrl}/videos/${request.mediaId}/${selection.sourceId}",
            headers = emptyMap(),
            mode = selection.mode,
            mimeType =
                when (selection.mode) {
                    PlaybackMode.DIRECT -> "video/mp4"
                    PlaybackMode.HLS -> "application/vnd.apple.mpegurl"
                    PlaybackMode.LOCAL -> "video/mp4"
                },
            subtitles = emptyList(),
            playSessionId = options.playSessionId ?: "session-${request.mediaId}",
            audioStreamIndex = options.audioStreamIndex ?: selection.defaultAudioTrack()?.streamIndex,
            subtitleStreamIndex = selection.defaultSubtitleTrack()?.streamIndex,
        )
}

private class QueuePlaybackSourceResolver(
    vararg sources: ResolvedPlaybackSource,
) : PlaybackSourceResolver {
    private val queue = ArrayDeque(sources.toList())

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource = queue.removeFirst()
}

private class RecordingAudioSwitchResolver : PlaybackSourceResolver {
    val requests = mutableListOf<PlaybackSourceOptions>()

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        requests += options
        return resolvedSource(
            url = "https://example.test/${request.mediaId}-${options.audioStreamIndex}.m3u8",
            mode = PlaybackMode.HLS,
            playSessionId = options.playSessionId ?: "play-audio",
            mediaSourceId = selection.sourceId,
        ).copy(audioStreamIndex = options.audioStreamIndex)
    }
}

private class FailingAudioSwitchResolver : PlaybackSourceResolver {
    private var calls = 0

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        calls += 1
        if (calls > 1) error("Audio switch failed")
        return resolvedSource(
            url = "https://example.test/${request.mediaId}.m3u8",
            mode = PlaybackMode.HLS,
            playSessionId = "play-audio",
            mediaSourceId = selection.sourceId,
        ).copy(audioStreamIndex = options.audioStreamIndex)
    }
}

private class DelayingAudioSwitchResolver(
    private val firstSwitchStarted: CompletableDeferred<Unit>,
    private val releaseFirstSwitch: CompletableDeferred<Unit>,
) : PlaybackSourceResolver {
    val requestedAudioIndices = mutableListOf<Int?>()

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        requestedAudioIndices += options.audioStreamIndex
        if (requestedAudioIndices.size == 2) {
            firstSwitchStarted.complete(Unit)
            releaseFirstSwitch.await()
        }
        return resolvedSource(
            url = "https://example.test/${request.mediaId}-${options.audioStreamIndex}.m3u8",
            mode = PlaybackMode.HLS,
            playSessionId = options.playSessionId ?: "play-audio",
            mediaSourceId = selection.sourceId,
        ).copy(audioStreamIndex = options.audioStreamIndex)
    }
}

private class BlockingInitialPlaybackResolver(
    private val firstResolveStarted: CompletableDeferred<Unit>,
    private val allowFirstResolve: CompletableDeferred<Unit>,
) : PlaybackSourceResolver {
    private var calls = 0

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        calls += 1
        if (calls == 1) {
            firstResolveStarted.complete(Unit)
            allowFirstResolve.await()
        }
        return resolvedSource(
            url = "https://example.test/${request.mediaId}.mp4",
            mode = PlaybackMode.DIRECT,
            playSessionId = "play-${request.mediaId}",
            mediaSourceId = selection.sourceId,
        )
    }
}

private class DelayingQualityResolver(
    private val firstSwitchStarted: CompletableDeferred<Unit>,
    private val releaseFirstSwitch: CompletableDeferred<Unit>,
    private val hlsFragment: String = "",
) : PlaybackSourceResolver {
    private var calls = 0

    override suspend fun resolve(
        request: PlaybackRequest,
        selection: PlaybackStreamSelection,
        environment: JellyfinEnvironment,
        startPositionMs: Long,
        options: PlaybackSourceOptions,
    ): ResolvedPlaybackSource {
        calls += 1
        if (calls == 2) {
            firstSwitchStarted.complete(Unit)
            releaseFirstSwitch.await()
        }
        val mode = if (calls == 1) PlaybackMode.DIRECT else PlaybackMode.HLS
        return resolvedSource(
            url =
                if (mode == PlaybackMode.DIRECT) {
                    "https://example.test/direct.mp4"
                } else {
                    "https://example.test/${selection.selectedQualityId}.m3u8$hlsFragment"
                },
            mode = mode,
            playSessionId = "play-$calls",
            mediaSourceId = selection.sourceId,
        )
    }
}

private fun resolvedSource(
    url: String,
    mode: PlaybackMode,
    playSessionId: String,
    mediaSourceId: String,
    supportsTranscoding: Boolean? = true,
): ResolvedPlaybackSource =
    ResolvedPlaybackSource(
        url = url,
        headers = emptyMap(),
        mode = mode,
        mimeType =
            when (mode) {
                PlaybackMode.DIRECT,
                PlaybackMode.LOCAL,
                -> "video/mp4"
                PlaybackMode.HLS -> "application/vnd.apple.mpegurl"
            },
        subtitles = emptyList(),
        playSessionId = playSessionId,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        mediaSourceId = mediaSourceId,
        supportsTranscoding = supportsTranscoding,
    )

private fun sampleItem(
    id: String = "item-1",
    positionTicks: Long? = null,
): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = null,
        name = "Sample Item",
        sortName = null,
        overview = null,
        type = "Movie",
        mediaType = "Video",
        locationType = "FileSystem",
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = positionTicks,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

private fun sampleDetail(
    withDirect: Boolean = false,
    directCodec: String = "h264",
    directSupportsDirectPlay: Boolean = true,
    directSupportsDirectStream: Boolean = false,
    includeSrt: Boolean = false,
    includeVtt: Boolean = false,
    includePgs: Boolean = false,
    includeSup: Boolean = false,
    includeAss: Boolean = false,
    includeSsa: Boolean = false,
    includeJapaneseAudio: Boolean = false,
    englishIsDefault: Boolean = true,
    durationMs: Long = 90_000L,
): JellyfinItemDetail {
    val baseStreams =
        mutableListOf(
            audioStream(
                index = 1,
                language = if (englishIsDefault) "en" else "jpn",
                isDefault = true,
            ),
            videoStream(displayTitle = "720p", codec = "h264"),
        )
    if (includeJapaneseAudio) {
        baseStreams +=
            audioStream(
                index = 8,
                language = if (englishIsDefault) "jpn" else "eng",
                isDefault = false,
            )
    }
    if (includeSrt) {
        baseStreams += subtitleStream(index = 2, codec = "srt", displayTitle = "English SRT")
    }
    if (includeVtt) {
        baseStreams += subtitleStream(index = 3, codec = "webvtt", displayTitle = "English VTT")
    }
    if (includePgs) {
        baseStreams += subtitleStream(index = 4, codec = "pgs", displayTitle = "Blu-ray PGS")
    }
    if (includeSup) {
        baseStreams += subtitleStream(index = 5, codec = "sup", displayTitle = "Blu-ray SUP")
    }
    if (includeAss) {
        baseStreams += subtitleStream(index = 6, codec = "ass", displayTitle = "Styled ASS")
    }
    if (includeSsa) {
        baseStreams += subtitleStream(index = 7, codec = "ssa", displayTitle = "Legacy SSA")
    }
    val sources =
        mutableListOf(
            mediaSource(
                id = "hls-source",
                supportsDirectPlay = false,
                supportsDirectStream = false,
                supportsTranscoding = true,
                streams = baseStreams.toList(),
            ),
        )
    if (withDirect) {
        sources +=
            mediaSource(
                id = "direct-source",
                supportsDirectPlay = directSupportsDirectPlay,
                supportsDirectStream = directSupportsDirectStream,
                supportsTranscoding = true,
                streams =
                    listOf(
                        videoStream(displayTitle = "1080p", codec = directCodec),
                        audioStream(index = 10, language = "en", isDefault = true),
                    ),
            )
    }
    return JellyfinItemDetail(
        id = "item-1",
        name = "Sample Detail",
        overview = null,
        taglines = emptyList(),
        runTimeTicks = durationMs * 10_000L,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        genres = emptyList(),
        studios = emptyList(),
        primaryImageTag = null,
        backdropImageTags = emptyList(),
        mediaSources = sources,
    )
}

private class RecordingStreamingProgressReporter : StreamingProgressReporter {
    val startEvents = mutableListOf<Pair<StreamingProgressContext, Long>>()
    val progressEvents = mutableListOf<Pair<StreamingProgressContext, Long>>()
    val completedEvents = mutableListOf<StreamingProgressContext>()

    override suspend fun onStart(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        startEvents += context to positionMs
    }

    override suspend fun onProgress(
        context: StreamingProgressContext,
        positionMs: Long,
    ) {
        progressEvents += context to positionMs
    }

    override suspend fun onCompleted(context: StreamingProgressContext) {
        completedEvents += context
    }
}

private class FakeCastSessionManager : CastSessionManager {
    private val _state = MutableSharedFlow<CastConnectionState>(replay = 1).apply { tryEmit(CastConnectionState.Idle) }
    private val _progress = MutableSharedFlow<Long>()
    override val connectionState: SharedFlow<CastConnectionState> = _state
    override val remoteProgress: SharedFlow<Long> = _progress
    val commands = mutableListOf<String>()

    override suspend fun play() {
        commands += "play"
    }

    override suspend fun pause() {
        commands += "pause"
    }

    override suspend fun seek(positionMs: Long) {
        commands += "seek:$positionMs"
    }

    override suspend fun stop() {
        commands += "stop"
    }

    override suspend fun selectSubtitleTrack(trackId: String?) {
        commands += "subtitle:${trackId ?: "off"}"
    }

    override suspend fun disconnect() {
        commands += "disconnect"
    }

    suspend fun emitState(state: CastConnectionState) {
        _state.emit(state)
    }

    suspend fun emitProgress(positionMs: Long) {
        _progress.emit(positionMs)
    }
}

private class RecordingPlayerEngine : PlayerEngine {
    private val _positionUpdates = MutableSharedFlow<Long>()
    private val _events = MutableSharedFlow<PlayerEvent>()
    override val positionUpdates: SharedFlow<Long> = _positionUpdates
    override val events: SharedFlow<PlayerEvent> = _events

    var playCount = 0
    var pauseCount = 0
    var stopCount = 0
    val seekPositions = mutableListOf<Long>()
    var lastPrepared: ResolvedPlaybackSource? = null
    var lastStartPositionMs: Long? = null
    var lastAudioTrack: AudioTrack? = null
    var lastSubtitleTrack: SubtitleTrack? = null
    var lastQuality: Int? = null
    var released = false
    var prepareCount = 0
    var audioSelectionResult = AudioTrackSelectionResult.APPLIED

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        prepareCount++
        lastPrepared = source
        lastStartPositionMs = startPositionMs
        lastAudioTrack = audioTrack
        lastSubtitleTrack = subtitleTrack
    }

    override fun play() {
        playCount++
    }

    override fun pause() {
        pauseCount++
    }

    override fun stop() {
        stopCount++
    }

    override fun seekTo(positionMs: Long) {
        seekPositions += positionMs
    }

    override fun setAudioTrack(track: AudioTrack?): AudioTrackSelectionResult {
        lastAudioTrack = track
        return audioSelectionResult
    }

    override fun setSubtitleTrack(track: SubtitleTrack?) {
        lastSubtitleTrack = track
    }

    override fun setVideoQuality(maxBitrate: Int?) {
        lastQuality = maxBitrate
    }

    override fun release() {
        released = true
    }

    suspend fun emitPosition(positionMs: Long) {
        _positionUpdates.emit(positionMs)
    }

    suspend fun emitEvent(event: PlayerEvent) {
        _events.emit(event)
    }
}

private class BlockingPlayerEngine : PlayerEngine {
    val allowPrepare = CompletableDeferred<Unit>()
    override val positionUpdates: SharedFlow<Long> = MutableSharedFlow()
    override val events: SharedFlow<PlayerEvent> = MutableSharedFlow()

    override suspend fun prepare(
        source: ResolvedPlaybackSource,
        startPositionMs: Long,
        audioTrack: AudioTrack?,
        subtitleTrack: SubtitleTrack?,
    ) {
        allowPrepare.await()
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setAudioTrack(track: AudioTrack?): AudioTrackSelectionResult = AudioTrackSelectionResult.PENDING

    override fun setSubtitleTrack(track: SubtitleTrack?) = Unit

    override fun setVideoQuality(maxBitrate: Int?) = Unit

    override fun release() = Unit
}

private fun mediaSource(
    id: String,
    supportsDirectPlay: Boolean,
    supportsDirectStream: Boolean,
    supportsTranscoding: Boolean,
    streams: List<JellyfinMediaStream>,
    videoBitrate: Int? = 8_000_000,
): JellyfinMediaSource =
    JellyfinMediaSource(
        id = id,
        name = id,
        runTimeTicks = null,
        container = "mp4",
        videoBitrate = videoBitrate,
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
        streams = streams,
    )

private fun videoStream(
    index: Int = 0,
    displayTitle: String,
    codec: String,
    bitrate: Int? = null,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.VIDEO,
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = null,
        isDefault = true,
        isForced = false,
        bitrate = bitrate,
    )

private fun audioStream(
    index: Int,
    language: String,
    isDefault: Boolean,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.AUDIO,
        index = index,
        displayTitle = "$language Audio",
        codec = "aac",
        language = language,
        isDefault = isDefault,
        isForced = false,
    )

private fun subtitleStream(
    index: Int,
    codec: String,
    displayTitle: String,
): JellyfinMediaStream =
    JellyfinMediaStream(
        type = JellyfinMediaStreamType.SUBTITLE,
        index = index,
        displayTitle = displayTitle,
        codec = codec,
        language = "en",
        isDefault = index == 2,
        isForced = false,
    )

private fun String.queryParameter(name: String): String? =
    substringBefore('#')
        .substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .firstOrNull { parameter -> parameter.substringBefore('=') == name }
        ?.substringAfter('=')
