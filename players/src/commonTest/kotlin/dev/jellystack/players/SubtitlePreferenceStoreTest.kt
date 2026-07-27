package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubtitlePreferenceStoreTest {
    @Test
    fun selectedSubtitleAppliesToAnotherEpisodeInSameSeries() {
        val store = MemorySubtitlePreferenceStore()
        val firstEpisode = sampleEpisode(id = "s1e1", seriesId = "series-1")
        val secondEpisode = sampleEpisode(id = "s2e1", seriesId = "series-1")
        val selected = subtitle(id = "4", language = "ger", title = "German Forced", forced = true)

        store.write(firstEpisode.subtitlePreferenceScopeKey(), selected.toPreference())
        val preference = store.read(secondEpisode.subtitlePreferenceScopeKey())

        assertNotNull(preference)
        val resolved =
            listOf(
                subtitle(id = "12", language = "eng", title = "English", forced = false),
                subtitle(id = "18", language = "ger", title = "German Forced", forced = true),
            ).resolveSubtitlePreference(preference)
        assertEquals("18", resolved.trackId)
    }

    @Test
    fun disabledSubtitlePreferencePersistsForSeries() {
        val store = MemorySubtitlePreferenceStore()
        val firstEpisode = sampleEpisode(id = "s1e1", seriesName = "Same Show")
        val secondEpisode = sampleEpisode(id = "s1e2", seriesName = "Same Show")

        store.write(firstEpisode.subtitlePreferenceScopeKey(), disabledSubtitlePreference())
        val preference = store.read(secondEpisode.subtitlePreferenceScopeKey())

        assertNotNull(preference)
        val resolved = listOf(subtitle(id = "1", language = "ger", title = "German")).resolveSubtitlePreference(preference)
        assertTrue(resolved.disabled)
        assertEquals(null, resolved.trackId)
    }

    @Test
    fun rawTrackIdMismatchResolvesBySemanticFields() {
        val preference =
            subtitle(
                id = "old-track",
                language = "eng",
                title = "English SDH",
                format = SubtitleFormat.SRT,
                default = true,
            ).toPreference()

        val resolved =
            listOf(
                subtitle(id = "new-track", language = "eng", title = "English SDH", format = SubtitleFormat.SRT, default = true),
                subtitle(id = "old-track", language = "spa", title = "Spanish", format = SubtitleFormat.SRT),
            ).resolveSubtitlePreference(preference)

        assertEquals("new-track", resolved.trackId)
    }
}

private class MemorySubtitlePreferenceStore : SubtitlePreferenceStore {
    private val values = mutableMapOf<String, SubtitleTrackPreference>()

    override fun read(scopeKey: String): SubtitleTrackPreference? = values[scopeKey]

    override fun write(
        scopeKey: String,
        preference: SubtitleTrackPreference,
    ) {
        values[scopeKey] = preference
    }
}

private fun subtitle(
    id: String,
    language: String?,
    title: String?,
    format: SubtitleFormat = SubtitleFormat.SRT,
    default: Boolean = false,
    forced: Boolean = false,
    streamIndex: Int? = id.toIntOrNull(),
): SubtitleTrack =
    SubtitleTrack(
        id = id,
        language = language,
        title = title,
        format = format,
        isDefault = default,
        isForced = forced,
        streamIndex = streamIndex,
    )

private fun sampleEpisode(
    id: String,
    seriesId: String? = null,
    seriesName: String? = null,
): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = "library",
        name = "Episode",
        sortName = null,
        overview = null,
        type = "Episode",
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = seriesId,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = seriesName,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )
