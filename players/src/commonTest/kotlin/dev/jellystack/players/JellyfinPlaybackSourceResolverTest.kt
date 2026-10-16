package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackMediaSourceDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
                                            "/Videos/item-1/master.m3u8?VideoCodec=hevc&AllowVideoStreamCopy=true&ApiKey=dummy-token",
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
            assertModernQueryAuthentication(source)
            assertEquals(
                "https://demo.jellyfin.org/Videos/item-1/master.m3u8?VideoCodec=hevc&AllowVideoStreamCopy=true&ApiKey=dummy-token",
                source.url,
            )
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
            assertModernQueryAuthentication(source)
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
            assertModernQueryAuthentication(source)
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
    fun tvAutoRequestsRealDirectPlayAndUsesItWhenJellyfinApprovesTheSelectedTracks() =
        runTest {
            val service = RecordingPlaybackInfoService(directPlayResponse())
            val resolver =
                JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)))
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "eac3", channels = 6, streamIndex = 2)))

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            val sent = checkNotNull(service.lastRequest)
            assertEquals(PlaybackMode.DIRECT, source.mode)
            assertEquals(true, sent.enableDirectPlay)
            assertEquals(true, sent.enableDirectStream)
            assertEquals(true, sent.allowVideoStreamCopy)
            assertEquals(true, sent.allowAudioStreamCopy)
            assertEquals(2, sent.audioStreamIndex)
        }

    @Test
    fun tvDtsDefaultTrackUsesTheServerTranscodeUrlWithTheBestSupportedCodec() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    transcodeOnlyResponse(
                        "/Videos/item-1/master.m3u8?SegmentContainer=ts&VideoCodec=copy&AudioCodec=eac3&AudioStreamIndex=2" +
                            "&TranscodeReasons=AudioCodecNotSupported",
                    ),
                )
            val resolver =
                JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)))
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "dts", channels = 6, streamIndex = 2)))

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            val sent = checkNotNull(service.lastRequest)
            assertEquals(true, sent.enableDirectPlay)
            assertEquals(true, sent.allowAudioStreamCopy)
            assertEquals(
                "eac3,aac",
                sent.deviceProfile.transcodingProfiles
                    .first()
                    .audioCodec,
            )
            assertTrue(sent.deviceProfile.transcodingProfiles.all { it.maxAudioChannels == "6" })
            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals("eac3", source.url.queryParameter("AudioCodec"))
        }

    @Test
    fun tvTrustsTheServerTranscodeUrlOverABareDirectStreamAnswer() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = false,
                                    supportsDirectStream = true,
                                    supportsTranscoding = true,
                                    transcodingUrl = "/Videos/item-1/master.m3u8?VideoCodec=copy&AudioCodec=eac3",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val resolver =
                JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)))
            val request = videoRequest()
            val selection = PlaybackStreamSelector().select(request.mediaSources)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(PlaybackMode.HLS, source.mode)
        }

    @Test
    fun supportedTvSourceCodecIsRequestedAsIsOnTheFallbackUrlSoTheServerCopiesIt() =
        runTest {
            val service = RecordingPlaybackInfoService(noMethodResponse())
            val resolver =
                JellyfinPlaybackSourceResolver(
                    service,
                    tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AC3, PlaybackAudioCodec.AAC)),
                )
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "ac3", channels = 6, streamIndex = 1)))

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertTrue(source.isFallbackHls)
            assertEquals("ac3", source.url.queryParameter("AudioCodec"))
            assertEquals("true", source.url.queryParameter("AllowAudioStreamCopy"))
            assertEquals("6", source.url.queryParameter("TranscodingMaxAudioChannels"))
            assertEquals("1", source.url.queryParameter("AudioStreamIndex"))
        }

    @Test
    fun unsupportedTvSourceCodecFallsBackToTheBestSupportedTargetWithItsChannelLimit() =
        runTest {
            val service = RecordingPlaybackInfoService(noMethodResponse())
            val resolver =
                JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)))
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "dts", channels = 6, streamIndex = 2)))

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals("eac3", source.url.queryParameter("AudioCodec"))
            assertEquals("6", source.url.queryParameter("TranscodingMaxAudioChannels"))
            assertEquals("true", source.url.queryParameter("AllowAudioStreamCopy"))
        }

    @Test
    fun aacOnlyTvTranscodesUnsupportedAudioToStereoAac() =
        runTest {
            val service = RecordingPlaybackInfoService(noMethodResponse())
            val resolver = JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.AAC)))
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "dts", channels = 6, streamIndex = 2)))

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            val sent = checkNotNull(service.lastRequest)
            assertTrue(sent.deviceProfile.transcodingProfiles.all { it.audioCodec == "aac" && it.maxAudioChannels == "2" })
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("2", source.url.queryParameter("TranscodingMaxAudioChannels"))
        }

    @Test
    fun audioOutputFallbackOnTvLeavesDirectPlayForServerSelectedStereoAacHls() =
        runTest {
            val service = RecordingPlaybackInfoService(directPlayResponse())
            val resolver =
                JellyfinPlaybackSourceResolver(
                    service,
                    tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AC3, PlaybackAudioCodec.AAC)),
                )
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "eac3", channels = 6, streamIndex = 1)))

            val source =
                resolver.resolve(
                    request,
                    selection,
                    environment(),
                    0,
                    PlaybackSourceOptions(forceAudioTranscoding = true),
                )

            val sent = checkNotNull(service.lastRequest)
            assertEquals(PlaybackMode.HLS, source.mode)
            assertEquals(false, sent.enableDirectPlay)
            assertEquals(false, sent.allowAudioStreamCopy)
            assertTrue(sent.deviceProfile.transcodingProfiles.all { it.audioCodec == "aac" && it.maxAudioChannels == "2" })
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("2", source.url.queryParameter("TranscodingMaxAudioChannels"))
            assertEquals("false", source.url.queryParameter("AllowAudioStreamCopy"))
        }

    @Test
    fun audioTrackSwitchGetsTheBestTargetOnTvAndTheStereoCompatibilityRenditionElsewhere() =
        runTest {
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "dts", channels = 6, streamIndex = 2)))
            val tvService = RecordingPlaybackInfoService(directPlayResponse())
            val phoneService = RecordingPlaybackInfoService(directPlayResponse())
            val phoneProfile =
                PlaybackDeviceProfileProvider {
                    PlaybackDeviceProfileFactory.create(
                        "Phone",
                        PlaybackDecoderCapabilities(
                            videoCodecs = setOf(PlaybackVideoCodec.H264),
                            audioCodecs = setOf(PlaybackAudioCodec.AAC, PlaybackAudioCodec.AC3),
                        ),
                    )
                }

            val tvSource =
                JellyfinPlaybackSourceResolver(
                    tvService,
                    tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)),
                ).resolve(request, selection, environment(), 0, PlaybackSourceOptions(selectAudioOnServer = true))
            val phoneSource =
                JellyfinPlaybackSourceResolver(phoneService, phoneProfile)
                    .resolve(request, selection, environment(), 0, PlaybackSourceOptions(selectAudioOnServer = true))

            assertEquals(PlaybackMode.HLS, tvSource.mode)
            assertEquals(false, tvService.lastRequest?.enableDirectPlay)
            assertEquals("eac3", tvSource.url.queryParameter("AudioCodec"))
            assertEquals(true, tvService.lastRequest?.allowAudioStreamCopy)
            assertEquals("aac", phoneSource.url.queryParameter("AudioCodec"))
            assertEquals(false, phoneService.lastRequest?.allowAudioStreamCopy)
            assertTrue(
                phoneService.lastRequest
                    ?.deviceProfile
                    ?.transcodingProfiles
                    .orEmpty()
                    .all { it.audioCodec == "aac" },
            )
        }

    @Test
    fun tvOnlyHandsTracksItCannotDecodeToTheServer() {
        val service = RecordingPlaybackInfoService(directPlayResponse())
        val resolver =
            JellyfinPlaybackSourceResolver(service, tvProvider(tvSnapshot(PlaybackAudioCodec.EAC3, PlaybackAudioCodec.AAC)))

        assertTrue(resolver.canPlayAudioCodecLocally("eac3"))
        assertTrue(resolver.canPlayAudioCodecLocally("AAC"))
        assertFalse(resolver.canPlayAudioCodecLocally("dts"))
        assertFalse(resolver.canPlayAudioCodecLocally("truehd"))
        assertTrue(resolver.canPlayAudioCodecLocally(null))
        assertTrue(JellyfinPlaybackSourceResolver(service).canPlayAudioCodecLocally("dts"))
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
    fun hlsFallbackUrlDeliversOnlyTextSubtitlesExternallySoTheServerDoesNotBurnThemIn() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
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
            val tracks =
                listOf(SubtitleFormat.SRT, SubtitleFormat.PGS).mapIndexed { index, format ->
                    SubtitleTrack(
                        id = "subtitle-$index",
                        language = "en",
                        title = "English",
                        format = format,
                        isDefault = false,
                        isForced = false,
                        streamIndex = index + 1,
                    )
                }
            val selection = PlaybackStreamSelector().select(request.mediaSources).copy(subtitleTracks = tracks)

            val textSource =
                resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions(subtitleStreamIndex = 1))
            val bitmapSource =
                resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions(subtitleStreamIndex = 2))

            assertEquals("External", textSource.url.queryParameter("SubtitleMethod"))
            assertNull(bitmapSource.url.queryParameter("SubtitleMethod"))
            assertEquals("2", bitmapSource.url.queryParameter("SubtitleStreamIndex"))
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
            assertModernQueryAuthentication(source)
            assertEquals("320000", source.url.queryParameter("MaxStreamingBitrate"))
            assertEquals("audio/aac", source.mimeType)
            assertNull(service.lastRequest)

            val directSource =
                resolver.resolve(request, selection.copy(mode = PlaybackMode.DIRECT), environment(), 0, PlaybackSourceOptions())

            assertTrue(directSource.url.contains("/Audio/song-1/stream.flac"))
            assertModernQueryAuthentication(directSource)
            assertNull(service.lastRequest)
        }

    @Test
    fun subtitleUrlsAuthenticateWithModernQueryParameterForEveryTextFormat() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = true,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val tracks =
                listOf(SubtitleFormat.SRT, SubtitleFormat.VTT, SubtitleFormat.ASS).mapIndexed { index, format ->
                    SubtitleTrack(
                        id = "subtitle-$index",
                        language = "en",
                        title = "English",
                        format = format,
                        isDefault = false,
                        isForced = false,
                        streamIndex = index + 1,
                    )
                }
            val selection = PlaybackStreamSelector().select(request.mediaSources).copy(subtitleTracks = tracks)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertModernQueryAuthentication(source)
            assertEquals(tracks.size, source.subtitles.size)
            source.subtitles.forEachIndexed { index, subtitle ->
                assertEquals("dummy-token", subtitle.url.queryParameter("ApiKey"))
                assertNull(subtitle.url.queryParameter("api_key"))
                assertEquals(tracks[index].id, subtitle.trackId)
                assertTrue(subtitle.url.contains("/Videos/item-1/source-1/Subtitles/${index + 1}/stream."))
            }
        }

    @Test
    fun bitmapSubtitlesAreNeverSideloadedAsText() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    container = "mkv",
                                    supportsDirectPlay = true,
                                ),
                            ),
                    ),
                )
            val resolver = JellyfinPlaybackSourceResolver(service, FixedPlaybackDeviceProfileProvider)
            val request = videoRequest()
            val tracks =
                listOf(SubtitleFormat.SRT, SubtitleFormat.PGS, SubtitleFormat.SUP).mapIndexed { index, format ->
                    SubtitleTrack(
                        id = "subtitle-$index",
                        language = "en",
                        title = "English",
                        format = format,
                        isDefault = false,
                        isForced = false,
                        streamIndex = index + 1,
                    )
                }
            val selection = PlaybackStreamSelector().select(request.mediaSources).copy(subtitleTracks = tracks)

            val source = resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            assertEquals(listOf("subtitle-0"), source.subtitles.map(ResolvedSubtitle::trackId))
        }

    @Test
    fun jellyfinBitmapSubtitleCodecsAreRecognisedAsBitmapFormats() {
        fun format(codec: String) =
            JellyfinMediaStream(
                type = JellyfinMediaStreamType.SUBTITLE,
                index = 3,
                displayTitle = null,
                codec = codec,
                language = "eng",
                isDefault = false,
                isForced = false,
            ).toSubtitleTrack()?.format

        assertEquals(SubtitleFormat.PGS, format("PGSSUB"))
        assertEquals(SubtitleFormat.SUP, format("dvd_subtitle"))
        assertEquals(SubtitleFormat.SRT, format("subrip"))
    }

    @Test
    fun tvProfileNeverOffersDtsAndTranscodesTheSelectedDtsTrackToTheBestSupportedCodec() =
        runTest {
            val service =
                RecordingPlaybackInfoService(
                    JellyfinPlaybackInfoResponseDto(
                        mediaSources =
                            listOf(
                                JellyfinPlaybackMediaSourceDto(
                                    id = "source-1",
                                    supportsTranscoding = true,
                                    transcodingUrl = "/Videos/item-1/master.m3u8?VideoCodec=copy&AudioCodec=eac3",
                                    transcodingContainer = "ts",
                                    transcodingSubProtocol = "hls",
                                ),
                            ),
                    ),
                )
            val everythingSupported =
                PlaybackCapabilitySnapshot(
                    videoSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                    videoHardwareSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                    audioSupport = PlaybackAudioCodec.entries.associateWith { CapabilitySupport.SUPPORTED },
                    maxAudioChannelCounts = PlaybackAudioCodec.entries.associateWith { 8 },
                    inspectionCompleteness = PlaybackCapabilityInspection.COMPLETE,
                )
            val resolver = JellyfinPlaybackSourceResolver(service, tvProvider(everythingSupported))
            val request = videoRequest()
            val selection =
                PlaybackStreamSelector()
                    .select(request.mediaSources)
                    .copy(audioTracks = listOf(audioTrack(codec = "dts", channels = 6, streamIndex = 2)))

            resolver.resolve(request, selection, environment(), 0, PlaybackSourceOptions())

            val sent = checkNotNull(service.lastRequest)
            assertEquals(2, sent.audioStreamIndex)
            assertTrue(sent.allowAudioStreamCopy)
            assertTrue(sent.enableDirectPlay)
            val offeredAudioCodecs =
                (sent.deviceProfile.directPlayProfiles.map { it.audioCodec } + sent.deviceProfile.transcodingProfiles.map { it.audioCodec })
                    .flatMap { codecs -> codecs.split(',') }
            assertFalse("dts" in offeredAudioCodecs)
            sent.deviceProfile.transcodingProfiles.forEach { profile ->
                assertTrue(profile.audioCodec.startsWith("eac3,ac3,aac,mp3"), profile.audioCodec)
                assertEquals("6", profile.maxAudioChannels)
            }
        }

    private fun tvProvider(snapshot: PlaybackCapabilitySnapshot) =
        object : PlaybackDeviceProfileProvider {
            override fun deviceProfile() = PlaybackDeviceProfileFactory.create("Test TV", selectTvDecoderCapabilities(snapshot))

            override fun usesBestAvailableAudioTranscodeTarget(): Boolean = true
        }

    /** Complete snapshot where H.264 and exactly [audio] are playable, each reporting 8 input channels. */
    private fun tvSnapshot(vararg audio: PlaybackAudioCodec) =
        PlaybackCapabilitySnapshot(
            videoSupport =
                PlaybackVideoCodec.entries.associateWith { CapabilitySupport.UNSUPPORTED } +
                    (PlaybackVideoCodec.H264 to CapabilitySupport.SUPPORTED),
            videoHardwareSupport = PlaybackVideoCodec.entries.associateWith { CapabilitySupport.UNSUPPORTED },
            audioSupport =
                PlaybackAudioCodec.entries.associateWith {
                    if (it in audio) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED
                },
            maxAudioChannelCounts = audio.associateWith { 8 },
            inspectionCompleteness = PlaybackCapabilityInspection.COMPLETE,
        )

    private fun audioTrack(
        codec: String,
        channels: Int?,
        streamIndex: Int,
    ) = AudioTrack(
        id = streamIndex.toString(),
        language = "eng",
        title = "English",
        codec = codec,
        isDefault = true,
        streamIndex = streamIndex,
        channels = channels,
    )

    /** PlaybackInfo where Jellyfin approves Direct Play of the selected tracks. */
    private fun directPlayResponse() =
        JellyfinPlaybackInfoResponseDto(
            playSessionId = "play-direct",
            mediaSources =
                listOf(
                    JellyfinPlaybackMediaSourceDto(
                        id = "source-1",
                        container = "mkv",
                        supportsDirectPlay = true,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                    ),
                ),
        )

    /** PlaybackInfo where Jellyfin rejects Direct Play and returns its own transcode URL. */
    private fun transcodeOnlyResponse(transcodingUrl: String) =
        JellyfinPlaybackInfoResponseDto(
            playSessionId = "play-transcode",
            mediaSources =
                listOf(
                    JellyfinPlaybackMediaSourceDto(
                        id = "source-1",
                        container = "mkv",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = true,
                        transcodingUrl = transcodingUrl,
                        transcodingContainer = "ts",
                        transcodingSubProtocol = "hls",
                    ),
                ),
        )

    /** PlaybackInfo without any method or URL, which makes the resolver build the HLS URL itself. */
    private fun noMethodResponse() =
        JellyfinPlaybackInfoResponseDto(
            playSessionId = "play-no-method",
            mediaSources =
                listOf(
                    JellyfinPlaybackMediaSourceDto(
                        id = "source-1",
                        container = "mkv",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = true,
                    ),
                ),
        )

    private fun assertModernQueryAuthentication(source: ResolvedPlaybackSource) {
        assertEquals("dummy-token", source.url.queryParameter("ApiKey"))
        assertNull(source.url.queryParameter("api_key"))
        assertFalse(
            source.headers.keys.any { name ->
                name.equals("Authorization", ignoreCase = true) ||
                    name.startsWith("X-Emby-", ignoreCase = true) ||
                    name.startsWith("X-MediaBrowser-", ignoreCase = true)
            },
        )
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
