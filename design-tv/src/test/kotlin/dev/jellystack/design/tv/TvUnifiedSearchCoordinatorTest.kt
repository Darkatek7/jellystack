package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.profile.MediaProviderIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvUnifiedSearchCoordinatorTest {
    @Test
    fun restoredQueryIsSubmittedExactlyOnceToBothSources() =
        runTest {
            val jellyfinQueries = mutableListOf<String>()
            val seerrQueries = mutableListOf<String>()
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    initialSession = TvSearchSessionState("restored", mode = TvSearchMode.BROWSE),
                    sources =
                        TvSearchSources(
                            jellyfin = { query ->
                                jellyfinQueries += query
                                listOf(jellyfinItem("jf"))
                            },
                            seerr = { query ->
                                seerrQueries += query
                                listOf(seerrItem("seerr"))
                            },
                        ),
                )

            advanceUntilIdle()
            coordinator.restoreQuery("restored")
            advanceUntilIdle()

            assertEquals(listOf("restored"), jellyfinQueries)
            assertEquals(listOf("restored"), seerrQueries)
            assertEquals(TvSearchMode.BROWSE, coordinator.state.value.session.mode)
        }

    @Test
    fun staleSourceCompletionsCannotOverwriteANewerQuery() =
        runTest {
            val oldStarted = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    sources =
                        TvSearchSources(
                            jellyfin = { query ->
                                if (query == "old") {
                                    oldStarted.complete(Unit)
                                    withContext(NonCancellable) { releaseOld.await() }
                                }
                                listOf(jellyfinItem(query))
                            },
                            seerr = { query -> listOf(seerrItem(query)) },
                        ),
                )

            coordinator.search("old")
            oldStarted.await()
            coordinator.search("new")
            advanceUntilIdle()
            releaseOld.complete(Unit)
            advanceUntilIdle()

            val state = coordinator.state.value
            assertEquals("new", state.session.query)
            assertEquals(listOf("new"), state.jellyfin.items.map(JellyfinItem::id))
            assertEquals(listOf("new"), state.seerr.items.map(JellyseerrSearchItem::title))
        }

    @Test
    fun partialFailureKeepsTheSuccessfulSourceAndRetryContent() =
        runTest {
            var failSeerr = true
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    sources =
                        TvSearchSources(
                            jellyfin = { listOf(jellyfinItem("available")) },
                            seerr = {
                                if (failSeerr) error("offline")
                                listOf(seerrItem("requestable"))
                            },
                        ),
                )

            coordinator.search("dune")
            advanceUntilIdle()
            assertEquals(
                listOf("available"),
                coordinator.state.value.jellyfin.items
                    .map(JellyfinItem::id),
            )
            assertEquals("offline", coordinator.state.value.seerr.errorMessage)

            failSeerr = false
            coordinator.retrySeerr()
            assertEquals(
                listOf("available"),
                coordinator.state.value.jellyfin.items
                    .map(JellyfinItem::id),
            )
            advanceUntilIdle()
            assertEquals(
                listOf("requestable"),
                coordinator.state.value.seerr.items
                    .map(JellyseerrSearchItem::title),
            )
        }

    @Test
    fun voiceCancellationAndFailurePreserveQueryResultsAndMode() =
        runTest {
            val voice = FakeVoiceSearchPort(TvVoiceSearchAvailability.AVAILABLE)
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    voiceSearch = voice,
                    sources =
                        TvSearchSources(
                            jellyfin = { listOf(jellyfinItem("existing")) },
                            seerr = { emptyList() },
                        ),
                )
            coordinator.search("existing query")
            coordinator.enterBrowseMode()
            advanceUntilIdle()
            val before = coordinator.state.value

            coordinator.launchVoiceSearch()
            voice.complete(TvVoiceSearchResult.Cancelled)
            assertEquals(before.copy(isVoiceListening = false), coordinator.state.value)

            coordinator.launchVoiceSearch()
            voice.complete(TvVoiceSearchResult.Error("recognizer failed"))
            val afterFailure = coordinator.state.value
            assertEquals(before.session, afterFailure.session)
            assertEquals(before.jellyfin, afterFailure.jellyfin)
            assertEquals(before.seerr, afterFailure.seerr)
            assertEquals("recognizer failed", afterFailure.voiceError)
        }

    @Test
    fun unsupportedVoiceSearchCannotLaunch() =
        runTest {
            val voice = FakeVoiceSearchPort(TvVoiceSearchAvailability.UNAVAILABLE)
            val coordinator =
                TvSearchCoordinator(
                    scope = this,
                    debounceMillis = 0L,
                    voiceSearch = voice,
                    sources = TvSearchSources(jellyfin = { emptyList() }, seerr = { emptyList() }),
                )

            coordinator.launchVoiceSearch()

            assertEquals(0, voice.launchCount)
            assertFalse(coordinator.state.value.isVoiceListening)
            assertFalse(coordinator.state.value.showVoiceAction)
        }

    private class FakeVoiceSearchPort(
        override val availability: TvVoiceSearchAvailability,
    ) : TvVoiceSearchPort {
        var launchCount = 0
        private var callback: ((TvVoiceSearchResult) -> Unit)? = null

        override fun launch(onResult: (TvVoiceSearchResult) -> Unit) {
            launchCount += 1
            callback = onResult
        }

        fun complete(result: TvVoiceSearchResult) {
            assertTrue(callback != null)
            callback?.invoke(result)
            callback = null
        }
    }
}

internal fun jellyfinItem(
    id: String,
    name: String = id,
    type: String = "Movie",
    tmdbId: String? = null,
    tvdbId: String? = null,
) = JellyfinItem(
    id = id,
    libraryId = "library",
    name = name,
    sortName = null,
    overview = null,
    type = type,
    mediaType = "Video",
    locationType = null,
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
    positionTicks = null,
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
    providerIds = MediaProviderIds(tmdbId = tmdbId, tvdbId = tvdbId, sourceLocalId = id),
)

internal fun seerrItem(
    title: String,
    mediaType: JellyseerrMediaType = JellyseerrMediaType.MOVIE,
    tmdbId: Int = 1,
    tvdbId: Int? = null,
) = JellyseerrSearchItem(
    tmdbId = tmdbId,
    mediaType = mediaType,
    title = title,
    overview = null,
    releaseYear = null,
    posterPath = null,
    backdropPath = null,
    mediaInfoId = null,
    tvdbId = tvdbId,
    availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
    requests = emptyList(),
)
