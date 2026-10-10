package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

/** [TvSearchActions] from lambdas, so a test only names the actions it watches. */
internal fun testSearchActions(
    onQueryChanged: (String) -> Unit = {},
    onSourceChanged: (TvSearchSource) -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onEnterBrowseMode: () -> Unit = {},
    onRetryJellyfin: () -> Unit = {},
    onRetrySeerr: () -> Unit = {},
    onVoiceSearch: () -> Unit = {},
    onJellyfinItem: (JellyfinItem) -> Unit = {},
    onSeerrItem: (JellyseerrSearchItem) -> Unit = {},
): TvSearchActions =
    object : TvSearchActions {
        override fun onQueryChanged(query: String) = onQueryChanged.invoke(query)

        override fun onSourceChanged(source: TvSearchSource) = onSourceChanged.invoke(source)

        override fun onEnterEditMode() = onEnterEditMode.invoke()

        override fun onEnterBrowseMode() = onEnterBrowseMode.invoke()

        override fun onRetryJellyfin() = onRetryJellyfin.invoke()

        override fun onRetrySeerr() = onRetrySeerr.invoke()

        override fun onVoiceSearch() = onVoiceSearch.invoke()

        override fun onJellyfinItem(item: JellyfinItem) = onJellyfinItem.invoke(item)

        override fun onSeerrItem(item: JellyseerrSearchItem) = onSeerrItem.invoke(item)
    }
