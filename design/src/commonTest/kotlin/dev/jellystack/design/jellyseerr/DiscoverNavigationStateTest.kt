package dev.jellystack.design.jellyseerr

import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfileOption
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMessageCode
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.jellyseerr.JellyseerrSeasonStatus
import dev.jellystack.core.jellyseerr.JellyseerrUser
import dev.jellystack.design.navigation.DiscoverDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoverNavigationStateTest {
    @Test
    fun requestsRoundTripPreservesFeedAndRequestState() {
        val initial =
            DiscoverUiState(
                destination = DiscoverDestination.Feed,
                feedScrollKey = "feed:rail-4:item-8",
                requestQuery = "Dune",
                requestFilter = JellyseerrRequestFilter.PENDING,
            )

        val requests = initial.reduce(DiscoverAction.OpenRequests)
        val returned = requests.reduce(DiscoverAction.BackToFeed)

        assertEquals(DiscoverDestination.Feed, returned.destination)
        assertEquals(initial.feedScrollKey, returned.feedScrollKey)
        assertEquals("Dune", returned.requestQuery)
        assertEquals(JellyseerrRequestFilter.PENDING, returned.requestFilter)
    }

    @Test
    fun closingConfigurationDoesNotClearSelectionInputs() {
        val item = searchItem(tmdbId = 10, title = "Dune")
        val configured =
            DiscoverUiState(destination = DiscoverDestination.Requests)
                .reduce(DiscoverAction.SelectSearchResult(item))
                .reduce(DiscoverAction.SelectProfile(JellyseerrRequestProfileSelection.ServerDefault))
                .reduce(
                    DiscoverAction.SelectSeasonSelection(
                        JellyseerrCreateSelection.Seasons(listOf(1)),
                    ),
                )

        val closed = configured.reduce(DiscoverAction.CloseSelection)
        val reopened = closed.reduce(DiscoverAction.SelectSearchResult(item))

        assertEquals(JellyseerrRequestProfileSelection.ServerDefault, reopened.pendingProfileSelection)
        assertEquals(
            JellyseerrCreateSelection.Seasons(listOf(1)),
            reopened.pendingSeasonSelection,
        )
        assertNull(closed.selected)
    }

    @Test
    fun requestConfigurationDoesNotLeakAcrossMovieAndTvWithTheSameTmdbId() {
        val movie = searchItem(tmdbId = 10, title = "Movie", mediaType = JellyseerrMediaType.MOVIE)
        val show = searchItem(tmdbId = 10, title = "Show", mediaType = JellyseerrMediaType.TV)
        val movieProfile =
            JellyseerrRequestProfileSelection.Profile(
                JellyseerrLanguageProfileOption(
                    languageProfileId = 11,
                    name = "Movie HD",
                    serviceId = 21,
                    serviceName = "Radarr",
                    is4k = false,
                    isDefault = false,
                    profileId = 31,
                ),
            )
        val configuredMovie =
            DiscoverUiState()
                .reduce(DiscoverAction.SelectSearchResult(movie))
                .reduce(DiscoverAction.SelectProfile(movieProfile))
                .reduce(DiscoverAction.SelectSeasonSelection(JellyseerrCreateSelection.Seasons(listOf(2))))

        val showDetail = configuredMovie.reduce(DiscoverAction.SelectSearchResult(show))

        assertEquals(JellyseerrRequestProfileSelection.ServerDefault, showDetail.pendingProfileSelection)
        assertEquals(JellyseerrCreateSelection.AllSeasons, showDetail.pendingSeasonSelection)
    }

    @Test
    fun submissionsStayOnDetailAndSuccessfulSubmitClosesOnlyConfiguration() {
        val item = searchItem(tmdbId = 10, title = "Dune")
        val submitOperation = DiscoverPendingOperation.Submit(item.mediaType, item.tmdbId)
        val configured =
            DiscoverUiState(
                destination = DiscoverDestination.Requests,
                requestQuery = "Dune",
                requestFilter = JellyseerrRequestFilter.PENDING,
            ).reduce(DiscoverAction.SelectSearchResult(item))
                .reduce(DiscoverAction.SelectSeasonSelection(JellyseerrCreateSelection.Seasons(listOf(1))))
                .reduce(DiscoverAction.OperationStarted(submitOperation))

        val duplicate =
            configured.reduce(
                DiscoverAction.OperationFinished(
                    JellyseerrMessageCode.RequestDuplicate,
                    submitOperation.operationKey,
                ),
            )
        assertNotNull(duplicate.selected)
        assertEquals("Dune", duplicate.requestQuery)
        assertEquals(JellyseerrRequestFilter.PENDING, duplicate.requestFilter)
        assertEquals(JellyseerrCreateSelection.Seasons(listOf(1)), duplicate.pendingSeasonSelection)
        assertNull(duplicate.pendingOperation)

        val succeeded =
            duplicate
                .reduce(DiscoverAction.OpenRequestConfiguration)
                .reduce(DiscoverAction.OperationStarted(submitOperation))
                .reduce(
                    DiscoverAction.OperationFinished(
                        JellyseerrMessageCode.RequestSubmitted,
                        submitOperation.operationKey,
                    ),
                )
        assertNotNull(succeeded.selected)
        assertNull(succeeded.pendingOperation)
        assertEquals(false, succeeded.isRequestConfigurationOpen)
        assertEquals("Dune", succeeded.requestQuery)
    }

    @Test
    fun olderSubmitCompletionCannotClearNewerMediaConfiguration() {
        val first = searchItem(tmdbId = 10, title = "First")
        val second = searchItem(tmdbId = 20, title = "Second")
        val firstOperation =
            DiscoverPendingOperation.Submit(first.mediaType, first.tmdbId)
        val secondOperation =
            DiscoverPendingOperation.Submit(second.mediaType, second.tmdbId)
        val awaitingSecond =
            DiscoverUiState()
                .reduce(DiscoverAction.SelectSearchResult(first))
                .reduce(DiscoverAction.OpenRequestConfiguration)
                .reduce(DiscoverAction.OperationStarted(firstOperation))
                .reduce(DiscoverAction.SelectSearchResult(second))
                .reduce(DiscoverAction.OpenRequestConfiguration)
                .reduce(DiscoverAction.OperationStarted(secondOperation))

        val firstCompletedLate =
            awaitingSecond.reduce(
                DiscoverAction.OperationFinished(
                    JellyseerrMessageCode.RequestSubmitted,
                    firstOperation.operationKey,
                ),
            )

        assertEquals(secondOperation, firstCompletedLate.pendingOperation)
        assertTrue(firstCompletedLate.isRequestConfigurationOpen)
        assertEquals(second.tmdbId, firstCompletedLate.selected?.key?.tmdbId)

        val secondCompleted =
            firstCompletedLate.reduce(
                DiscoverAction.OperationFinished(
                    JellyseerrMessageCode.RequestSubmitted,
                    secondOperation.operationKey,
                ),
            )
        assertNull(secondCompleted.pendingOperation)
        assertFalse(secondCompleted.isRequestConfigurationOpen)
    }

    @Test
    fun requestManagementKeepsDetailAfterMatchingSuccessfulOperation() {
        val summary = requestSummary(id = 201)
        val approveOperation = DiscoverPendingOperation.Approve(summary.id)
        val selected =
            DiscoverUiState(destination = DiscoverDestination.Requests)
                .reduce(DiscoverAction.SelectExistingRequest(summary))
                .reduce(DiscoverAction.OperationStarted(approveOperation))

        val failed =
            selected.reduce(
                DiscoverAction.OperationFinished(
                    JellyseerrMessageCode.ApprovalFailed,
                    approveOperation.operationKey,
                ),
            )
        assertNotNull(failed.selected)

        val succeeded =
            failed
                .reduce(DiscoverAction.OperationStarted(approveOperation))
                .reduce(
                    DiscoverAction.OperationFinished(
                        JellyseerrMessageCode.RequestApproved,
                        approveOperation.operationKey,
                    ),
                )
        assertNotNull(succeeded.selected)
    }

    @Test
    fun recommendationDetailsStayOnFeedUntilRequestConfigurationIsExplicitlyOpened() {
        val item = searchItem(tmdbId = 10, title = "Dune")

        val details =
            DiscoverUiState()
                .reduce(DiscoverAction.SelectRecommendation(JellyseerrRecommendationRail.TRENDS, item, 0))

        assertEquals(DiscoverDestination.Feed, details.destination)
        assertEquals(SeerrDetailOrigin.Trends, details.selected?.origin)
        assertEquals(false, details.isRequestConfigurationOpen)

        val configuration = details.reduce(DiscoverAction.OpenRequestConfiguration)
        assertEquals(DiscoverDestination.Feed, configuration.destination)
        assertEquals(true, configuration.isRequestConfigurationOpen)

        val returned = configuration.reduce(DiscoverAction.CloseRequestConfiguration)
        assertEquals(SeerrDetailOrigin.Trends, returned.selected?.origin)
        assertEquals(false, returned.isRequestConfigurationOpen)
    }

    @Test
    fun searchOpensDetailBeforeRequestConfiguration() {
        val item = searchItem(tmdbId = 10, title = "Dune")

        val detail =
            DiscoverUiState(destination = DiscoverDestination.Requests)
                .reduce(DiscoverAction.SelectSearchResult(item))

        assertEquals(SeerrDetailOrigin.Search, detail.selected?.origin)
        assertEquals(false, detail.isRequestConfigurationOpen)
    }

    @Test
    fun nestedDetailsPopBeforeOriginAndRestoreTypedTabAndScrollState() {
        val parent = searchItem(tmdbId = 10, title = "Dune")
        val child = searchItem(tmdbId = 11, title = "Dune: Part Two")
        val parentViewState =
            SeerrDetailViewState(
                selectedSection = JellyseerrDetailSection.Info,
                firstVisibleItemIndex = 4,
                firstVisibleItemScrollOffset = 72,
            )

        val nested =
            DiscoverUiState()
                .reduce(
                    DiscoverAction.SelectRecommendation(
                        JellyseerrRecommendationRail.TRENDS,
                        parent,
                        0,
                    ),
                ).reduce(
                    DiscoverAction.UpdateDetailViewState(
                        key = SeerrDetailKey(parent.mediaType, parent.tmdbId),
                        viewState = parentViewState,
                    ),
                ).reduce(
                    DiscoverAction.OpenRelatedDetail(
                        item = child,
                        origin = SeerrDetailOrigin.Similar,
                    ),
                )

        assertEquals(2, nested.detailBackStack.size)
        assertEquals(SeerrDetailOrigin.Similar, nested.selected?.origin)

        val returned = nested.reduce(DiscoverAction.CloseSelection)
        assertEquals(parent.tmdbId, returned.selected?.key?.tmdbId)
        assertEquals(parentViewState, returned.selected?.viewState)
        assertEquals(DiscoverDestination.Feed, returned.destination)
    }

    @Test
    fun existingTvRequestOnlyOffersSeasonsThatHaveNotAlreadyBeenRequested() {
        val request =
            requestSummary(id = 201).copy(
                mediaType = JellyseerrMediaType.TV,
                seasons =
                    listOf(
                        JellyseerrSeasonStatus(1, JellyseerrRequestStatus.COMPLETED),
                        JellyseerrSeasonStatus(3, JellyseerrRequestStatus.APPROVED),
                    ),
            )

        assertEquals(listOf(2, 4), requestableSeasons(listOf(1, 2, 3, 4), request))
        assertEquals("Dune", request.toSearchItemOrNull()?.title)
        assertNull(request.copy(tmdbId = null).toSearchItemOrNull())
    }

    @Test
    fun primaryCommandsAreStatusAwareAndAvailableIsNeverAnAction() {
        assertEquals(
            SeerrPrimaryAction.Request,
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.MOVIE,
                mediaStatus = null,
                hasRequest = false,
                requestableSeasons = emptyList(),
            ).primaryAction,
        )
        listOf(
            JellyseerrMediaStatus.PENDING,
            JellyseerrMediaStatus.PROCESSING,
            JellyseerrMediaStatus.AVAILABLE,
        ).forEach { status ->
            val command =
                resolveSeerrRequestCommand(
                    mediaType = JellyseerrMediaType.MOVIE,
                    mediaStatus = status,
                    hasRequest = status != JellyseerrMediaStatus.AVAILABLE,
                    requestableSeasons = emptyList(),
                )
            assertNull(command.primaryAction)
            assertTrue(command.showStatus)
        }
        assertEquals(
            SeerrPrimaryAction.RequestMoreSeasons,
            resolveSeerrRequestCommand(
                mediaType = JellyseerrMediaType.TV,
                mediaStatus = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                hasRequest = true,
                requestableSeasons = listOf(2, 3),
            ).primaryAction,
        )
    }

    @Test
    fun deletePermissionAllowsAdminOrOwnerButNotAnotherUser() {
        val request =
            requestSummary(id = 201).copy(
                requestedBy =
                    JellyseerrUser(
                        id = 7,
                        displayName = "Owner",
                        username = "owner",
                        permissions = 0,
                    ),
            )

        assertTrue(canDeleteSeerrRequest(request, isAdmin = true, currentUserId = 8))
        assertTrue(canDeleteSeerrRequest(request, isAdmin = false, currentUserId = 7))
        assertFalse(canDeleteSeerrRequest(request, isAdmin = false, currentUserId = 8))
        assertFalse(canDeleteSeerrRequest(request, isAdmin = false, currentUserId = null))
    }

    @Test
    fun requestsOriginPrefersExactRequestWhenMediaHasMultipleRequests() {
        val selected =
            requestSummary(id = 201).copy(
                mediaType = JellyseerrMediaType.TV,
                requestStatus = JellyseerrRequestStatus.COMPLETED,
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                        `4k` = null,
                    ),
                seasons =
                    listOf(
                        JellyseerrSeasonStatus(
                            seasonNumber = 1,
                            status = JellyseerrRequestStatus.COMPLETED,
                        ),
                    ),
                requestedBy =
                    JellyseerrUser(
                        id = 7,
                        displayName = "Selected owner",
                        username = "selected",
                        permissions = 0,
                    ),
            )
        val sameMedia =
            selected.copy(
                id = 202,
                requestStatus = JellyseerrRequestStatus.PENDING,
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PENDING,
                        `4k` = null,
                    ),
                requestedBy =
                    JellyseerrUser(
                        id = 8,
                        displayName = "Other owner",
                        username = "other",
                        permissions = 0,
                    ),
            )
        val entry =
            requireNotNull(
                DiscoverUiState()
                    .reduce(DiscoverAction.SelectExistingRequest(selected))
                    .selected,
            )

        val resolved =
            requireNotNull(
                resolveSeerrDetailRequest(
                    entry = entry,
                    requests = listOf(selected, sameMedia),
                    currentRequestsByMedia =
                        mapOf(selected.mediaType to requireNotNull(selected.tmdbId) to sameMedia),
                ),
            )

        assertEquals(selected.id, resolved.id)
        assertEquals(
            SeerrPrimaryAction.RequestMoreSeasons,
            resolveSeerrRequestCommand(
                mediaType = resolved.mediaType,
                mediaStatus = resolved.availability.standard,
                hasRequest = true,
                requestableSeasons = requestableSeasons(listOf(1, 2), resolved),
            ).primaryAction,
        )
        assertTrue(canDeleteSeerrRequest(resolved, isAdmin = false, currentUserId = 7))
    }

    @Test
    fun requestsOriginUsesSameRequestFromLiveLookupAfterFilteredRefresh() {
        val pending = requestSummary(id = 201)
        val approved =
            pending.copy(
                requestStatus = JellyseerrRequestStatus.APPROVED,
            )
        val entry =
            requireNotNull(
                DiscoverUiState()
                    .reduce(DiscoverAction.SelectExistingRequest(pending))
                    .selected,
            )

        val resolved =
            requireNotNull(
                resolveSeerrDetailRequest(
                    entry = entry,
                    requests = emptyList(),
                    currentRequestsByMedia =
                        mapOf(pending.mediaType to requireNotNull(pending.tmdbId) to approved),
                ),
            )

        assertEquals(pending.id, resolved.id)
        assertEquals(JellyseerrRequestStatus.APPROVED, resolved.requestStatus)
    }

    @Test
    fun authoritativeLiveRequestSnapshotDoesNotResurrectDeletedRequest() {
        val deleted = requestSummary(id = 201)
        val entry =
            requireNotNull(
                DiscoverUiState()
                    .reduce(DiscoverAction.SelectExistingRequest(deleted))
                    .selected,
            )

        val resolved =
            resolveSeerrDetailRequest(
                entry = entry,
                requests = emptyList(),
                currentRequestsByMedia = emptyMap(),
                liveRequestStateAvailable = true,
            )

        assertNull(resolved)
        assertNull(
            resolveSeerrDetailAvailability(
                entry = entry,
                request = resolved,
                liveRequestStateAvailable = true,
            ).standard,
        )
    }

    @Test
    fun authoritativeLiveAbsenceClearsDeletedRequestStatusAcrossEveryDetailOrigin() {
        val deleted =
            requestSummary(id = 201).copy(
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PROCESSING,
                        `4k` = null,
                    ),
            )
        val staleItem = requireNotNull(deleted.toSearchItemOrNull())

        SeerrDetailOrigin.entries.forEach { origin ->
            val entry =
                SeerrDetailEntry(
                    key = SeerrDetailKey(staleItem.mediaType, staleItem.tmdbId),
                    item = staleItem,
                    origin = origin,
                    requestId = deleted.id,
                )

            assertNull(
                resolveSeerrDetailAvailability(
                    entry = entry,
                    request = null,
                    liveRequestStateAvailable = true,
                ).standard,
                origin.name,
            )
        }
    }

    @Test
    fun authoritativeLiveAbsenceKeepsAvailabilityThatWasNeverBoundToARequest() {
        val availableItem =
            searchItem(
                tmdbId = 10,
                title = "Available movie",
                mediaType = JellyseerrMediaType.MOVIE,
            ).copy(
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.AVAILABLE,
                        `4k` = null,
                    ),
            )
        val entry =
            SeerrDetailEntry(
                key = SeerrDetailKey(availableItem.mediaType, availableItem.tmdbId),
                item = availableItem,
                origin = SeerrDetailOrigin.Search,
            )

        assertEquals(
            JellyseerrMediaStatus.AVAILABLE,
            resolveSeerrDetailAvailability(
                entry = entry,
                request = null,
                liveRequestStateAvailable = true,
            ).standard,
        )
    }

    private fun searchItem(
        tmdbId: Int,
        title: String,
        mediaType: JellyseerrMediaType = JellyseerrMediaType.TV,
    ) = JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        overview = null,
        releaseYear = null,
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )

    private fun requestSummary(id: Int) =
        dev.jellystack.core.jellyseerr.JellyseerrRequestSummary(
            id = id,
            requestStatus = dev.jellystack.core.jellyseerr.JellyseerrRequestStatus.PENDING,
            mediaType = JellyseerrMediaType.MOVIE,
            mediaId = 90,
            tmdbId = 10,
            tvdbId = null,
            title = "Dune",
            originalTitle = null,
            posterPath = null,
            backdropPath = null,
            is4k = false,
            availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
            seasons = emptyList(),
            requestedBy = null,
            profileName = null,
            createdAt = null,
            updatedAt = null,
            canRemoveFromService = true,
        )
}
