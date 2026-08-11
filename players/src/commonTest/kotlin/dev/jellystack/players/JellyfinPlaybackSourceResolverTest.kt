package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackMediaSourceDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinPlaybackSourceResolverTest {
    @Test
    fun manualQualityIsNegotiatedInPlaybackInfoWithoutMutatingTheServerUrl() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    response =
                        JellyfinPlaybackInfoResponseDto(
                            playSessionId = "play-1",
                            mediaSources =
                                listOf(
                                    JellyfinPlaybackMediaSourceDto(
                                        id = "source-1",
                                        container = "ts",
                                        supportsDirectPlay = false,
                                        supportsDirectStream = false,
                                        supportsTranscoding = true,
                                        transcodingUrl =
                                            "/Videos/item-1/master.m3u8?VideoCodec=hevc&AllowVideoStreamCopy=true",
                                        transcodingContainer = "ts",
                                        transcodingSubProtocol = "hls",
                                    ),
                                ),
                        ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val option =
                PlaybackQualityOption(
                    id = "quality-hls-source-1-420000",
                    label = "420 Kbps · 360p",
                    mode = PlaybackMode.HLS,
                    sourceId = "source-1",
                    maxBitrate = 420_000,
                    maxHeight = 360,
                    isAuto = false,
                )
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources, option)

            val source = resolver.resolve(request, selection, environment(), 12_000, PlaybackSourceOptions())

            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals("hevc", source.url.queryParameter("VideoCodec"))
            assertEquals(null, source.url.queryParameter("VideoBitRate"))
            assertEquals(null, source.url.queryParameter("MaxHeight"))
            assertEquals("true", source.url.queryParameter("AllowVideoStreamCopy"))
            assertEquals("play-1", source.playSessionId)
            assertEquals("source-1", source.mediaSourceId)
            assertEquals(true, source.supportsTranscoding)
            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(false, service.lastRequest?.enableDirectStream)
            assertEquals(true, service.lastRequest?.enableTranscoding)
            assertEquals(false, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(true, service.lastRequest?.allowAudioStreamCopy)
            assertEquals(420_000, service.lastRequest?.maxStreamingBitrate)
            assertEquals(120_000_000, service.lastRequest?.startTimeTicks)
            assertEquals("source-1", service.lastRequest?.mediaSourceId)
        }

    @Test
    fun autoUsesServerApprovedDirectSource() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-auto",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = true,
                                    supportsTranscoding = true,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(PlaybackMode.DIRECT, source.mode)
            assertEquals("source-1", source.mediaSourceId)
            assertEquals("play-auto", source.playSessionId)
            assertTrue(source.url.contains("/Videos/item-1/stream.mkv"))
            assertEquals(true, service.lastRequest?.enableDirectPlay)
            assertEquals(true, service.lastRequest?.enableDirectStream)
            assertEquals(true, service.lastRequest?.enableTranscoding)
            assertEquals(true, service.lastRequest?.allowVideoStreamCopy)
            assertNull(service.lastRequest?.maxStreamingBitrate)
        }

    @Test
    fun autoUsesServerApprovedDirectStreamWhenTranscodingUrlIsAbsent() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-direct-stream",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = true,
                                    supportsTranscoding = true,
                                    transcodingUrl = null,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(PlaybackMode.DIRECT, source.mode)
            assertEquals("source-1", source.mediaSourceId)
            assertEquals("play-direct-stream", source.playSessionId)
            assertTrue(source.url.contains("/Videos/item-1/stream.mkv"))
            assertEquals("true", source.url.queryParameter("Static"))
            assertEquals("source-1", source.url.queryParameter("MediaSourceId"))
        }

    @Test
    fun autoFallsBackToHlsWhenServerRejectsDirectPlayback() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-fallback",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                    transcodingUrl = null,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(mode = PlaybackMode.DIRECT)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals("source-1", source.mediaSourceId)
            assertEquals("play-fallback", source.playSessionId)
            assertTrue(source.url.contains("/Videos/item-1/master.m3u8"))
            assertEquals("hevc", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertTrue(source.isFallbackHls)
        }

    @Test
    fun forcedTranscodingDisablesAllStreamCopyFlags() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-forced",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = true,
                                    supportsDirectStream = true,
                                    supportsTranscoding = true,
                                    transcodingUrl = "/Videos/item-1/master.m3u8?VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    23_000,
                    PlaybackSourceOptions(forceTranscoding = true),
                )

            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(false, service.lastRequest?.enableDirectStream)
            assertEquals(false, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(false, service.lastRequest?.allowAudioStreamCopy)
            assertEquals(null, source.url.queryParameter("EnableAutoStreamCopy"))
            assertEquals(null, source.url.queryParameter("AllowVideoStreamCopy"))
            assertEquals(null, source.url.queryParameter("AllowAudioStreamCopy"))
        }

    @Test
    fun forcedTvTranscodingDownmixesAacToAConservativeStereoStream() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-stereo-fallback",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?VideoCodec=h264&AudioCodec=aac&AudioBitrate=640000",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory.create(
                        name = "Test TV",
                        capabilities =
                            PlaybackDecoderCapabilities(
                                videoCodecs = setOf(PlaybackVideoCodec.H264),
                                audioCodecs = setOf(PlaybackAudioCodec.AAC),
                                maxAacChannelCount = 2,
                            ),
                    )
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    42_000,
                    PlaybackSourceOptions(forceTranscoding = true),
                )

            assertTrue(
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
                    .all { it.maxAudioChannels == "2" },
            )
            assertEquals("640000", source.url.queryParameter("AudioBitrate"))
            assertEquals(null, source.url.queryParameter("MaxAudioChannels"))
        }

    @Test
    fun tvAudioFallbackCopiesOriginalVideoAndOnlyTranscodesStereoAudio() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-audio-only",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?VideoCodec=copy&AudioCodec=aac&VideoBitrate=120000000",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory.create(
                        name = "Test TV",
                        capabilities =
                            PlaybackDecoderCapabilities(
                                videoCodecs = setOf(PlaybackVideoCodec.H264),
                                audioCodecs = setOf(PlaybackAudioCodec.AAC),
                                maxAacChannelCount = 2,
                                maxStreamingBitrate = 120_000_000,
                            ),
                    )
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    42_000,
                    PlaybackSourceOptions(forceAudioTranscoding = true),
                )

            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(true, service.lastRequest?.enableDirectStream)
            assertEquals(true, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(false, service.lastRequest?.allowAudioStreamCopy)
            assertEquals("copy", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("120000000", source.url.queryParameter("VideoBitrate"))
            assertEquals(null, source.url.queryParameter("AllowVideoStreamCopy"))
            assertTrue(
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
                    .all { it.maxAudioChannels == "2" },
            )
        }

    @Test
    fun tvAutoPolicyAllowsDirectPlayAtOriginalQualityFromTheStart() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-tv-auto",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsDirectPlay = true,
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=mp4&VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "mp4",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory
                        .create(
                            name = "Test TV",
                            capabilities =
                                PlaybackDecoderCapabilities(
                                    videoCodecs = setOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264),
                                    audioCodecs = setOf(PlaybackAudioCodec.AAC),
                                    maxAacChannelCount = 2,
                                    maxStreamingBitrate = 120_000_000,
                                ),
                        ).copy(codecProfiles = emptyList())
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    42_000,
                    PlaybackSourceOptions(),
                )

            assertEquals(true, service.lastRequest?.enableDirectPlay)
            assertEquals(true, service.lastRequest?.enableDirectStream)
            assertEquals(true, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(true, service.lastRequest?.allowAudioStreamCopy)
            assertEquals(120_000_000, service.lastRequest?.maxStreamingBitrate)
            assertEquals(PlaybackMode.DIRECT, source.mode)
        }

    @Test
    fun tvAudioFallbackTrustsTheServerNegotiatedContainerAndCodecs() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-tv-av1",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=ts&VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory
                        .create(
                            name = "Test TV",
                            capabilities =
                                PlaybackDecoderCapabilities(
                                    videoCodecs = setOf(PlaybackVideoCodec.AV1, PlaybackVideoCodec.H264),
                                    audioCodecs = setOf(PlaybackAudioCodec.AAC),
                                    maxAacChannelCount = 2,
                                    maxStreamingBitrate = 120_000_000,
                                ),
                        ).copy(codecProfiles = emptyList())
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources).copy(videoCodec = "av1")

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    0,
                    PlaybackSourceOptions(
                        forceAudioTranscoding = true,
                        stopEncodingPlaySessionId = "old-play-session",
                    ),
                )

            assertEquals(listOf("old-play-session"), service.stoppedSessions)
            assertEquals(false, service.lastRequest?.allowVideoStreamCopy)
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("ts", source.url.queryParameter("SegmentContainer"))
            assertEquals("ts", source.segmentContainer)
        }

    @Test
    fun tvAutoPolicyTranscodesAacWhenInitialChannelSafetyIsUnknown() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-tv-aac",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=ts&VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory
                        .create(
                            name = "Test TV",
                            capabilities =
                                PlaybackDecoderCapabilities(
                                    videoCodecs = setOf(PlaybackVideoCodec.AV1, PlaybackVideoCodec.H264),
                                    audioCodecs = setOf(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC),
                                    maxAacChannelCount = 2,
                                    maxAudioChannelCounts =
                                        mapOf(
                                            PlaybackAudioCodec.EAC3 to 6,
                                            PlaybackAudioCodec.AAC to 2,
                                        ),
                                    maxStreamingBitrate = 120_000_000,
                                ),
                        )
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(
                        videoCodec = "av1",
                        audioCodec = "aac",
                        audioTracks =
                            listOf(
                                AudioTrack(
                                    id = "1",
                                    language = "eng",
                                    title = "English 5.1",
                                    codec = "aac",
                                    isDefault = true,
                                    streamIndex = 1,
                                    channels = null,
                                ),
                            ),
                    )

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(false, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(false, service.lastRequest?.allowAudioStreamCopy)
            assertEquals(1, service.lastRequest?.audioStreamIndex)
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("ts", source.url.queryParameter("SegmentContainer"))
            assertTrue(
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
                    .all { it.maxAudioChannels == "2" },
            )
        }

    @Test
    fun tvInitialVideoStartUsesTheSameReliableAudioPathAsManualTrackSwitching() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-tv-initial-audio",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsDirectPlay = true,
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=ts&VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val tvProfile =
                object : PlaybackDeviceProfileProvider {
                    override fun deviceProfile() =
                        PlaybackDeviceProfileFactory.create(
                            name = "Test TV",
                            capabilities =
                                PlaybackDecoderCapabilities(
                                    videoCodecs = setOf(PlaybackVideoCodec.H264),
                                    audioCodecs = setOf(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC),
                                    maxAudioChannelCounts =
                                        mapOf(
                                            PlaybackAudioCodec.EAC3 to 6,
                                            PlaybackAudioCodec.AAC to 2,
                                        ),
                                ),
                        )

                    override fun requiresServerSelectedAudioForVideo(): Boolean = true
                }
            val resolver = JellyfinPlaybackSourceResolver(service, tvProfile)
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(
                        mode = PlaybackMode.DIRECT,
                        audioTracks =
                            listOf(
                                AudioTrack(
                                    id = "2",
                                    language = "eng",
                                    title = "English 5.1",
                                    codec = "eac3",
                                    isDefault = true,
                                    streamIndex = 2,
                                    channels = 6,
                                ),
                            ),
                    )

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(true, service.lastRequest?.allowVideoStreamCopy)
            assertEquals(false, service.lastRequest?.allowAudioStreamCopy)
            assertEquals(2, service.lastRequest?.audioStreamIndex)
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("ts", source.url.queryParameter("SegmentContainer"))
            assertTrue(
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
                    .all { it.maxAudioChannels == "2" },
            )
        }

    @Test
    fun forcedTranscodingKeepsTheBestCodecNegotiatedByTheServer() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-forced-hevc",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?VideoCodec=h264&RequireAvc=true&Profile=baseline",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    0,
                    PlaybackSourceOptions(forceTranscoding = true),
                )

            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("true", source.url.queryParameter("RequireAvc"))
            assertEquals("baseline", source.url.queryParameter("Profile"))
        }

    @Test
    fun forcedTranscodingOffersOnlyStableServerHlsTargetsAndKeepsTheNegotiatedUrl() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-compatibility-fallback",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=ts&VideoCodec=h264&AudioCodec=aac",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    732_000L,
                    PlaybackSourceOptions(forceTranscoding = true),
                )

            val profiles =
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
            assertTrue(profiles.isNotEmpty())
            assertTrue(profiles.all { "av1" !in it.videoCodec && "vp9" !in it.videoCodec })
            assertTrue(profiles.all { it.videoCodec.split(',').all { codec -> codec in setOf("hevc", "h264") } })
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("ts", source.url.queryParameter("SegmentContainer"))
        }

    @Test
    fun fmp4FallbackAdvertisesMp4HlsAndReturnsTheNegotiatedSegmentContainer() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "fmp4-session",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?SegmentContainer=mp4&VideoCodec=h264",
                                    transcodingContainer = "mp4",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    12_000L,
                    PlaybackSourceOptions(
                        forceTranscoding = true,
                        preferFmp4Hls = true,
                    ),
                )

            assertEquals(
                setOf("mp4"),
                service.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    ?.map { it.container }
                    ?.toSet(),
            )
            assertEquals(false, service.lastRequest?.enableDirectPlay)
            assertEquals(false, service.lastRequest?.enableDirectStream)
            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals("mp4", source.segmentContainer)
            assertTrue(source.url.contains("SegmentContainer=mp4"))
        }

    @Test
    fun manualQualityBuildsHlsFallbackWhenPlaybackInfoOffersNoMethod() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-hls-fallback",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                    transcodingUrl = null,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val option =
                PlaybackQualityOption(
                    id = "quality-hls-source-1-420000",
                    label = "420 Kbps · 360p",
                    mode = PlaybackMode.HLS,
                    sourceId = "source-1",
                    maxBitrate = 420_000,
                    maxHeight = 360,
                    isAuto = false,
                )
            val selection =
                PlaybackStreamSelector()
                    .select(
                        request.mediaSources,
                    ).copy(qualityOptions = listOf(option), selectedQualityId = option.id)

            val source = resolver.resolve(request, selection, environment(), 12_000, PlaybackSourceOptions())

            assertEquals(PlaybackMode.HLS, source.mode)
            assertTrue(source.url.contains("/Videos/item-1/master.m3u8"))
            assertEquals("hevc", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("164000", source.url.queryParameter("VideoBitRate"))
            assertEquals("360", source.url.queryParameter("MaxHeight"))
            assertEquals("0", source.url.queryParameter("StartTimeTicks"))
            assertEquals("play-hls-fallback", source.url.queryParameter("PlaySessionId"))
            assertTrue(source.isFallbackHls)
        }

    @Test
    fun autoFallbackKeepsSourceResolutionAndUsesAQualityVideoBitrate() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-auto-quality",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(videoWidth = 1_920, videoHeight = 1_080)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals("16000000", source.url.queryParameter("VideoBitRate"))
            assertEquals("1920", source.url.queryParameter("MaxWidth"))
            assertEquals("1080", source.url.queryParameter("MaxHeight"))
        }

    @Test
    fun autoFallbackAppliesAResolutionFloorToSuspiciouslyLowSourceBitrates() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-auto-floor",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(videoBitrate = 500_000, videoWidth = 1_920, videoHeight = 1_080)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals("16000000", source.url.queryParameter("VideoBitRate"))
            assertEquals("1920", source.url.queryParameter("MaxWidth"))
            assertEquals("1080", source.url.queryParameter("MaxHeight"))
        }

    @Test
    fun selectedSubtitleIndexIsSentToPlaybackInfoAndFallbackHls() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-subtitle",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val option =
                PlaybackQualityOption(
                    id = "manual",
                    label = "Manual",
                    mode = PlaybackMode.HLS,
                    sourceId = "source-1",
                    maxBitrate = 420_000,
                    maxHeight = 360,
                    isAuto = false,
                )
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(qualityOptions = listOf(option), selectedQualityId = option.id)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    0,
                    PlaybackSourceOptions(subtitleStreamIndex = 5),
                )

            assertEquals(5, service.lastRequest?.subtitleStreamIndex)
            assertEquals("5", source.url.queryParameter("SubtitleStreamIndex"))
            assertEquals(5, source.subtitleStreamIndex)
        }

    @Test
    fun disabledSubtitlesAreSentAsMinusOne() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        playSessionId = "play-subtitle-off",
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = false,
                                    supportsTranscoding = false,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val option =
                PlaybackQualityOption(
                    id = "manual-off",
                    label = "Manual",
                    mode = PlaybackMode.HLS,
                    sourceId = "source-1",
                    maxBitrate = 420_000,
                    maxHeight = 360,
                    isAuto = false,
                )
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(qualityOptions = listOf(option), selectedQualityId = option.id)

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    0,
                    PlaybackSourceOptions(subtitleStreamIndex = -1),
                )

            assertEquals(-1, service.lastRequest?.subtitleStreamIndex)
            assertEquals("-1", source.url.queryParameter("SubtitleStreamIndex"))
            assertEquals(-1, source.subtitleStreamIndex)
        }

    @Test
    fun omittedTranscodingFlagWithUsableUrlKeepsManualPlaybackAvailable() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    transcodingUrl =
                                        "/Videos/item-1/master.m3u8?videoCodec=h264&playsessionid=url-session",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val option =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .qualityOptions
                    .last { !it.isAuto }
            val selection = PlaybackStreamSelector().select(request.mediaSources, option)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals(true, source.supportsTranscoding)
            assertEquals("url-session", source.playSessionId)
            assertEquals(option.maxBitrate, service.lastRequest?.maxStreamingBitrate)
            assertEquals(null, source.url.queryParameter("VideoBitRate"))
        }

    @Test
    fun malformedPlaybackInfoDoesNotInventFallbackUrl() =
        runTest {
            val resolver =
                JellyfinPlaybackSourceResolver(
                    RecordingPlaybackInfoService(JellyfinPlaybackInfoResponseDto()),
                    FixedPlaybackDeviceProfileProvider,
                )
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            assertFailsWith<IllegalStateException> {
                resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())
            }
        }

    @Test
    fun resolvesAudioThroughJellyfinAudioRouteWithoutPlaybackInfoNegotiation() =
        runTest {
            val service = RecordingPlaybackInfoService(JellyfinPlaybackInfoResponseDto())
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request =
                PlaybackRequest(
                    mediaId = "song-1",
                    mediaSources =
                        listOf(
                            JellyfinMediaSource(
                                id = "audio-source",
                                name = "Audio",
                                runTimeTicks = 9_000_000,
                                container = "flac",
                                videoBitrate = null,
                                supportsDirectPlay = true,
                                supportsDirectStream = true,
                                supportsTranscoding = true,
                                streams = emptyList(),
                            ),
                        ),
                    mediaKind = PlaybackMediaKind.AUDIO,
                )
            val selection =
                PlaybackStreamSelection(
                    maxBitrate = 320_000,
                    qualityOptions = emptyList(),
                    selectedQualityId = PlaybackQualityOption.AUTO_ID,
                    sourceId = "audio-source",
                    mode = PlaybackMode.HLS,
                    container = "flac",
                    videoCodec = null,
                    audioCodec = "flac",
                    videoBitrate = null,
                    audioTracks = emptyList(),
                    subtitleTracks = emptyList(),
                )

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertTrue(source.url.contains("/Audio/song-1/universal"))
            assertEquals("320000", source.url.queryParameter("MaxStreamingBitrate"))
            assertEquals("audio/aac", source.mimeType)
            assertNull(service.lastRequest)
        }

    private fun videoRequest(): PlaybackRequest =
        PlaybackRequest(
            mediaId = "item-1",
            mediaSources =
                listOf(
                    JellyfinMediaSource(
                        id = "source-1",
                        name = "Source",
                        runTimeTicks = 9_000_000,
                        container = "mkv",
                        videoBitrate = 8_000_000,
                        supportsDirectPlay = true,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                        streams = emptyList(),
                    ),
                ),
        )

    private fun environment() =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://demo.jellyfin.org",
            accessToken = "dummy-token",
            userId = "user",
            deviceId = "device-id",
            deviceName = "TestDevice",
        )
}

private val FixedPlaybackDeviceProfileProvider =
    PlaybackDeviceProfileProvider {
        PlaybackDeviceProfileFactory.create(
            "Test",
            PlaybackDecoderCapabilities(setOf(PlaybackVideoCodec.HEVC, PlaybackVideoCodec.H264)),
        )
    }

private class RecordingPlaybackInfoService(
    private val response: JellyfinPlaybackInfoResponseDto,
) : JellyfinPlaybackInfoService {
    var lastRequest: JellyfinPlaybackInfoRequestDto? = null
    val stoppedSessions = mutableListOf<String>()

    override suspend fun stopEncoding(
        environment: JellyfinEnvironment,
        playSessionId: String,
    ) {
        stoppedSessions += playSessionId
    }

    override suspend fun fetch(
        environment: JellyfinEnvironment,
        itemId: String,
        userId: String,
        request: JellyfinPlaybackInfoRequestDto,
    ): JellyfinPlaybackInfoResponseDto {
        lastRequest = request
        return response
    }
}

private fun String.queryParameter(name: String): String? =
    substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .firstNotNullOfOrNull { parameter ->
            val key = parameter.substringBefore('=', missingDelimiterValue = parameter)
            parameter.substringAfter('=', missingDelimiterValue = "").takeIf { key.equals(name, ignoreCase = true) }
        }
