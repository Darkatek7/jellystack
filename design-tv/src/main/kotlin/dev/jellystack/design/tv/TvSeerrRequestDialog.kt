@file:Suppress("FunctionName", "FunctionNaming", "LongMethod", "LongParameterList", "MaxLineLength", "CyclomaticComplexMethod")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import dev.jellystack.core.jellyseerr.JellyseerrCreateSelection
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestProfileSelection
import dev.jellystack.core.jellyseerr.JellyseerrRequestVariant
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState

@Composable
internal fun TvRequestDialog(
    title: String,
    mediaType: JellyseerrMediaType,
    seasons: List<Int>,
    onlyListedSeasons: Boolean,
    requestsState: JellyseerrRequestsState.Ready,
    strings: TvStrings,
    onDismiss: () -> Unit,
    onSubmit: (JellyseerrRequestProfileSelection, JellyseerrCreateSelection?, JellyseerrRequestVariant) -> Unit,
) {
    var variant by remember { mutableStateOf(JellyseerrRequestVariant.STANDARD) }
    var profile by remember { mutableStateOf<JellyseerrRequestProfileSelection>(JellyseerrRequestProfileSelection.ServerDefault) }
    var allSeasons by remember { mutableStateOf(true) }
    var selectedSeasons by remember(seasons) { mutableStateOf(seasons.toSet()) }
    val capabilities = requestsState.capabilities
    val canStandard = capabilities.canRequest(mediaType, JellyseerrRequestVariant.STANDARD)
    val can4k = capabilities.canRequest(mediaType, JellyseerrRequestVariant.FOUR_K)
    LaunchedEffect(Unit) {
        if (!canStandard && can4k) variant = JellyseerrRequestVariant.FOUR_K
    }
    val profiles =
        if (!capabilities.canUseAdvancedRequests) {
            emptyList()
        } else {
            when (mediaType) {
                JellyseerrMediaType.MOVIE -> requestsState.languageProfiles.movies
                JellyseerrMediaType.TV -> requestsState.languageProfiles.tv
                else -> emptyList()
            }.filter { it.is4k == (variant == JellyseerrRequestVariant.FOUR_K) }
        }
    LaunchedEffect(variant) { profile = JellyseerrRequestProfileSelection.ServerDefault }
    // The first choice gets focus; without choices the send action does.
    val firstFocus = rememberTvDialogInitialFocus()
    val showVersions = canStandard && can4k
    val showSeasons = mediaType == JellyseerrMediaType.TV && seasons.isNotEmpty()
    val showProfiles = capabilities.canUseAdvancedRequests && profiles.isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .heightIn(max = 470.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                strings.messages.requestTitle(title),
                color = TvText,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (showVersions) {
                TvSectionTitle(strings.version)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        strings.standard,
                        { variant = JellyseerrRequestVariant.STANDARD },
                        primary = variant == JellyseerrRequestVariant.STANDARD,
                        selected = variant == JellyseerrRequestVariant.STANDARD,
                        focusRequester = firstFocus,
                    )
                    TvActionButton(
                        "4K",
                        { variant = JellyseerrRequestVariant.FOUR_K },
                        primary = variant == JellyseerrRequestVariant.FOUR_K,
                        selected = variant == JellyseerrRequestVariant.FOUR_K,
                    )
                }
            }
            if (showSeasons) {
                TvSectionTitle(strings.seasons)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding =
                        PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    item {
                        TvActionButton(
                            strings.all,
                            { allSeasons = true },
                            primary = allSeasons,
                            selected = allSeasons,
                            focusRequester = firstFocus.takeIf { !showVersions },
                        )
                    }
                    items(seasons) { season ->
                        val selected = allSeasons || season in selectedSeasons
                        TvActionButton(
                            "${strings.season} $season",
                            {
                                if (allSeasons) {
                                    allSeasons = false
                                    selectedSeasons = setOf(season)
                                } else {
                                    selectedSeasons =
                                        if (season in selectedSeasons) selectedSeasons - season else selectedSeasons + season
                                }
                            },
                            primary = selected,
                            selected = selected,
                        )
                    }
                }
            }
            if (showProfiles) {
                TvSectionTitle(strings.requestProfile)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding =
                        PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    item {
                        TvActionButton(
                            strings.serverDefault,
                            { profile = JellyseerrRequestProfileSelection.ServerDefault },
                            primary = profile is JellyseerrRequestProfileSelection.ServerDefault,
                            selected = profile is JellyseerrRequestProfileSelection.ServerDefault,
                            focusRequester = firstFocus.takeIf { !showVersions && !showSeasons },
                        )
                    }
                    items(profiles, key = { "${it.serviceId}:${it.languageProfileId}:${it.profileId}" }) { option ->
                        TvActionButton(
                            option.name,
                            { profile = JellyseerrRequestProfileSelection.Profile(option) },
                            primary = (profile as? JellyseerrRequestProfileSelection.Profile)?.option == option,
                            selected = (profile as? JellyseerrRequestProfileSelection.Profile)?.option == option,
                        )
                    }
                }
            }
            requestsState.message?.takeIf { it.kind.name == "ERROR" }?.let {
                Text(it.detail ?: strings.requestFailed, color = TvError, fontSize = 17.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    if (requestsState.isPerformingAction) strings.requesting else strings.sendRequest,
                    modifier = Modifier.width(230.dp),
                    focusRequester = firstFocus.takeIf { !showVersions && !showSeasons && !showProfiles },
                    primary = true,
                    enabled =
                        !requestsState.isPerformingAction &&
                            (mediaType != JellyseerrMediaType.TV || allSeasons || selectedSeasons.isNotEmpty()),
                    onClick = {
                        onSubmit(
                            profile,
                            when {
                                mediaType != JellyseerrMediaType.TV -> null
                                // Seasons another request already covers must not be sent again.
                                allSeasons && onlyListedSeasons -> JellyseerrCreateSelection.Seasons(seasons.sorted())
                                allSeasons -> JellyseerrCreateSelection.AllSeasons
                                else -> JellyseerrCreateSelection.Seasons(selectedSeasons.sorted())
                            },
                            variant,
                        )
                    },
                )
                TvActionButton(strings.cancel, onDismiss, modifier = Modifier.width(150.dp))
            }
        }
    }
}
