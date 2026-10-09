package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinVideoLabelsTest {
    @Test
    fun letterboxedAndCroppedEncodesKeepTheirResolutionClass() {
        assertEquals(JellyfinVideoResolution.FULL_HD, jellyfinVideoResolution(1920, 800))
        assertEquals(JellyfinVideoResolution.FULL_HD, jellyfinVideoResolution(1440, 1080))
        assertEquals(JellyfinVideoResolution.UHD_4K, jellyfinVideoResolution(3840, 1600))
        assertEquals(JellyfinVideoResolution.HD, jellyfinVideoResolution(1280, 534))
        assertEquals(JellyfinVideoResolution.SD, jellyfinVideoResolution(720, 576))
    }

    @Test
    fun missingDimensionsHaveNoLabel() {
        assertNull(jellyfinVideoResolution(null, null))
        assertNull(jellyfinVideoResolution(0, -1))
        assertEquals(JellyfinVideoResolution.FULL_HD, jellyfinVideoResolution(null, 1080))
    }

    @Test
    fun detailUsesTheFirstVideoStreamWithDimensions() {
        val detail =
            detail(
                stream(JellyfinMediaStreamType.AUDIO, width = null, height = null),
                stream(JellyfinMediaStreamType.VIDEO, width = null, height = null),
                stream(JellyfinMediaStreamType.VIDEO, width = 1920, height = 800),
            )

        assertEquals(JellyfinVideoResolution.FULL_HD, detail.primaryVideoResolution())
        assertNull(detail().primaryVideoResolution())
    }

    private fun stream(
        type: JellyfinMediaStreamType,
        width: Int?,
        height: Int?,
    ) = JellyfinMediaStream(
        type = type,
        index = null,
        displayTitle = null,
        codec = null,
        language = null,
        isDefault = false,
        isForced = false,
        width = width,
        height = height,
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
            mediaSources =
                listOf(
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
                    ),
                ),
        )
}
