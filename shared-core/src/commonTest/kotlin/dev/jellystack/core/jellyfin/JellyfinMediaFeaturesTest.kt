package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinMediaFeaturesTest {
    @Test
    fun videoRangeTypeMapsToTheMostSpecificHdrFormat() {
        val cases =
            listOf(
                Triple("DOVI", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithHDR10", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithHLG", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithSDR", "SDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithEL", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithHDR10Plus", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("DOVIWithELHDR10Plus", "HDR", JellyfinDynamicRange.DOLBY_VISION),
                Triple("HDR10Plus", "HDR", JellyfinDynamicRange.HDR10_PLUS),
                Triple("HDR10", "HDR", JellyfinDynamicRange.HDR10),
                Triple("HLG", "HDR", JellyfinDynamicRange.HLG),
                Triple("DOVIInvalid", "HDR", JellyfinDynamicRange.HDR),
                Triple("Unknown", "HDR", JellyfinDynamicRange.HDR),
                Triple(null, "HDR", JellyfinDynamicRange.HDR),
                Triple("DOVIInvalid", "SDR", null),
                Triple("SDR", "SDR", null),
                Triple(null, null, null),
            )
        cases.forEach { (rangeType, range, expected) ->
            val actual = features(video(videoRangeType = rangeType, videoRange = range)).dynamicRange
            assertEquals(expected, actual, "VideoRangeType=$rangeType, VideoRange=$range")
        }
    }

    @Test
    fun audioDescribesTheBestTrackNotOnlyTheDefaultOne() {
        val features =
            features(
                video(),
                audio(profile = null, channels = 6, isDefault = true),
                audio(profile = "Dolby TrueHD + Dolby Atmos", channels = 8),
            )

        assertEquals(JellyfinImmersiveAudio.DOLBY_ATMOS, features.immersiveAudio)
        assertEquals(JellyfinChannelLayout.SURROUND_7_1, features.surround)
    }

    @Test
    fun immersiveAudioComesFromTheAudioProfileOnly() {
        val atmos = audio(profile = "Dolby Digital Plus + Dolby Atmos", channels = 6)
        val dtsX = audio(profile = "DTS-HD MA + DTS:X IMAX", channels = 8)
        val dtsHd = audio(profile = "DTS-HD MA", channels = 8)
        val atmosOnlyInTitle = audio(profile = null, channels = 8, displayTitle = "English - TrueHD Atmos 7.1")

        assertEquals(JellyfinImmersiveAudio.DOLBY_ATMOS, features(atmos).immersiveAudio)
        assertEquals(JellyfinImmersiveAudio.DTS_X, features(dtsX).immersiveAudio)
        assertNull(features(dtsHd).immersiveAudio)
        assertNull(features(atmosOnlyInTitle).immersiveAudio)
    }

    @Test
    fun onlySurroundLayoutsAreReported() {
        assertNull(features(audio(profile = null, channels = 2), audio(profile = null, channels = 1)).surround)
        assertNull(features(audio(profile = null, channels = 7)).surround)
        assertEquals(JellyfinChannelLayout.SURROUND_5_1, features(audio(profile = null, channels = 6)).surround)
    }

    @Test
    fun onlyHearingImpairedSubtitlesCountAsCaptions() {
        assertFalse(features(subtitle(hearingImpaired = false)).hearingImpairedSubtitles)
        assertTrue(features(subtitle(hearingImpaired = false), subtitle(hearingImpaired = true)).hearingImpairedSubtitles)
    }

    @Test
    fun onlyTheFirstMediaSourceCounts() {
        val first = detail(video(), audio(profile = null, channels = 2))
        val second =
            source(
                video(videoRangeType = "DOVI", videoRange = "HDR"),
                audio(profile = "Dolby TrueHD + Dolby Atmos", channels = 8),
            )

        assertEquals(
            JellyfinMediaFeatures(
                resolution = JellyfinVideoResolution.FULL_HD,
                dynamicRange = null,
                immersiveAudio = null,
                surround = null,
                hearingImpairedSubtitles = false,
            ),
            first.copy(mediaSources = first.mediaSources + second).mediaFeatures(),
        )
    }

    private fun features(vararg streams: JellyfinMediaStream) = detail(*streams).mediaFeatures()

    private fun video(
        videoRangeType: String? = null,
        videoRange: String? = null,
    ) = stream(JellyfinMediaStreamType.VIDEO).copy(
        width = 1920,
        height = 1080,
        profile = "Main 10",
        videoRangeType = videoRangeType,
        videoRange = videoRange,
    )

    private fun audio(
        profile: String?,
        channels: Int,
        isDefault: Boolean = false,
        displayTitle: String? = null,
    ) = stream(JellyfinMediaStreamType.AUDIO).copy(
        profile = profile,
        channels = channels,
        isDefault = isDefault,
        displayTitle = displayTitle,
    )

    private fun subtitle(hearingImpaired: Boolean) = stream(JellyfinMediaStreamType.SUBTITLE).copy(isHearingImpaired = hearingImpaired)

    private fun stream(type: JellyfinMediaStreamType) =
        JellyfinMediaStream(
            type = type,
            index = null,
            displayTitle = null,
            codec = null,
            language = null,
            isDefault = false,
            isForced = false,
        )

    private fun source(vararg streams: JellyfinMediaStream) =
        JellyfinMediaSource(
            id = "source",
            name = null,
            runTimeTicks = null,
            container = null,
            videoBitrate = null,
            supportsDirectPlay = true,
            supportsDirectStream = true,
            supportsTranscoding = true,
            streams = streams.toList(),
        )

    private fun detail(vararg streams: JellyfinMediaStream) =
        JellyfinItemDetail(
            id = "movie",
            name = "Movie",
            overview = null,
            taglines = emptyList(),
            runTimeTicks = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            genres = emptyList(),
            studios = emptyList(),
            primaryImageTag = null,
            backdropImageTags = emptyList(),
            mediaSources = listOf(source(*streams)),
        )
}
