package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import dev.jellystack.core.preferences.MotionPreference
import dev.jellystack.players.AndroidPlayerEngine

/** What the stage shows before a card has focus; [eyebrow] names the screen and stays while cards change. */
@Immutable
internal data class TvCinematicHero(
    val title: String,
    val overview: String? = null,
    val eyebrow: String? = null,
)

/**
 * [subtitle] is the line on the card itself; the stage shows [metadata] instead when there is one, and
 * [logoUrl] in place of the title text.
 */
@Immutable
internal data class TvCinematicCard(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val artworkUrl: String? = null,
    val backdropUrl: String? = null,
    val selected: Boolean = false,
    val played: Boolean = false,
    val resumeFraction: Float? = null,
    val logoUrl: String? = null,
    val metadata: String? = null,
) {
    init {
        require(id.isNotBlank()) { "A cinematic card requires a stable ID" }
        require(title.isNotBlank()) { "A cinematic card requires a title" }
        require(resumeFraction == null || resumeFraction in 0f..1f) { "Resume progress must be between zero and one" }
    }
}

@Immutable
internal data class TvCinematicRow(
    val id: String,
    val title: String,
    val cards: List<TvCinematicCard>,
) {
    init {
        require(id.isNotBlank()) { "A cinematic row requires a stable ID" }
        require(title.isNotBlank()) { "A cinematic row requires a title" }
        require(cards.map(TvCinematicCard::id).distinct().size == cards.size) { "Card IDs must be unique within a row" }
    }
}

@Immutable
internal data class TvCinematicBackdrop(
    val url: String? = null,
    val previousUrl: String? = null,
    val transitionMillis: Int = 0,
    val revision: Long = 0,
)

internal enum class TvCinematicStatusKind { LOADING, ERROR, INFO }

@Immutable
internal data class TvCinematicInlineStatus(
    val message: String,
    val kind: TvCinematicStatusKind,
)

@Immutable
internal data class TvCinematicBrowseState(
    val hero: TvCinematicHero? = null,
    val rows: List<TvCinematicRow> = emptyList(),
    val focusedAnchor: TvFocusAnchor? = null,
    val backdrop: TvCinematicBackdrop = TvCinematicBackdrop(),
    val inlineStatus: TvCinematicInlineStatus? = null,
) {
    init {
        require(rows.map(TvCinematicRow::id).distinct().size == rows.size) { "Cinematic row IDs must be unique" }
    }

    val focusedCard: TvCinematicCard?
        get() =
            focusedAnchor?.let { anchor ->
                rows.firstOrNull { it.id == anchor.sectionId }?.cards?.firstOrNull { it.id == anchor.itemId }
            }
}

/**
 * The Play key on a focused card: it plays [canPlay] cards with [onPlay], and the stage names the key in a hint
 * next to OK for Details.
 */
@Stable
internal class TvCinematicCardPlayback(
    val labels: TvCinematicHintLabels,
    val canPlay: (TvCinematicCard) -> Boolean,
    val onPlay: (TvCinematicCard) -> Unit,
)

@Immutable
internal data class TvCinematicHintLabels(
    val play: String,
    val resume: String,
    val details: String,
)

/** The focused card's trailer while it plays behind a cinematic browse screen. */
@Stable
internal class TvCinematicTrailer(
    val engine: AndroidPlayerEngine,
    val label: String,
    val soundEnabled: Boolean,
    val progress: State<Float>,
)

@Immutable
internal data class TvCinematicGeometry(
    val artworkWidthDp: Float,
    val artworkHeightDp: Float,
    val cardSpacingDp: Float,
    val focusHaloPaddingDp: Float,
    val minimumActionSizeDp: Float,
)

internal fun tvCinematicGeometry(): TvCinematicGeometry =
    TvCinematicGeometry(
        artworkWidthDp = TvLayoutTokens.LandscapeArtworkWidth.value,
        artworkHeightDp = TvLayoutTokens.LandscapeArtworkHeight.value,
        cardSpacingDp = TvLayoutTokens.CardSpacing.value,
        focusHaloPaddingDp = TvLayoutTokens.FocusHaloPadding.value,
        minimumActionSizeDp = TvLayoutTokens.MinimumActionSize.value,
    )

@Immutable
internal data class TvCinematicMotion(
    val reducedMotion: Boolean,
    val highContrastFocus: Boolean,
    val focusScale: Float,
    val focusRingWidthDp: Float,
    val crossfadeMillis: Int,
)

internal fun tvMotionReduced(
    preference: MotionPreference,
    systemAnimationsEnabled: Boolean,
): Boolean = !systemAnimationsEnabled || preference == MotionPreference.REDUCED

internal fun tvCinematicMotion(
    reducedMotion: Boolean,
    highContrastFocus: Boolean,
): TvCinematicMotion =
    TvCinematicMotion(
        reducedMotion = reducedMotion,
        highContrastFocus = highContrastFocus,
        focusScale = if (reducedMotion) 1f else TvLayoutTokens.FOCUS_SCALE,
        focusRingWidthDp = if (highContrastFocus) 7f else 5f,
        crossfadeMillis = if (reducedMotion) 0 else TV_BACKDROP_CROSSFADE_MILLIS,
    )
