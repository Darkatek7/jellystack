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
    fun manualQualityUsesNegotiatedCodecAndForcesBitrateResolutionAndVideoEncode() =
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
            assertEquals("420000", source.url.queryParameter("VideoBitRate"))
            assertEquals("360", source.url.queryParameter("MaxHeight"))
            assertEquals("false", source.url.queryParameter("EnableAutoStreamCopy"))
            assertEquals("false", source.url.queryParameter("AllowVideoStreamCopy"))
            assertEquals("true", source.url.queryParameter("AllowAudioStreamCopy"))
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
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
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
            assertEquals("false", source.url.queryParameter("EnableAutoStreamCopy"))
            assertEquals("false", source.url.queryParameter("AllowVideoStreamCopy"))
            assertEquals("false", source.url.queryParameter("AllowAudioStreamCopy"))
        }

    @Test
    fun forcedTranscodingPrefersTheBestCodecAdvertisedByTheDevice() =
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

            assertEquals("hevc", source.url.queryParameter("VideoCodec"))
            assertEquals("false", source.url.queryParameter("RequireAvc"))
            assertEquals("", source.url.queryParameter("Profile"))
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
            assertEquals("h264", source.url.queryParameter("VideoCodec"))
            assertEquals("aac", source.url.queryParameter("AudioCodec"))
            assertEquals("420000", source.url.queryParameter("VideoBitRate"))
            assertEquals("360", source.url.queryParameter("MaxHeight"))
            assertEquals("0", source.url.queryParameter("StartTimeTicks"))
            assertEquals("play-hls-fallback", source.url.queryParameter("PlaySessionId"))
            assertTrue(source.isFallbackHls)
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
            assertEquals(option.maxBitrate.toString(), source.url.queryParameter("VideoBitRate"))
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
