@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvProgram
import kotlinx.datetime.Instant
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** 2026-01-15 20:12 UTC: twelve minutes into the guide's first half hour, so the now line stands clear of the edge. */
private const val GOLDEN_GUIDE_NOW_MILLIS = 1_768_507_920_000L

private val GOLDEN_GUIDE_ORIGIN = Instant.parse("2026-01-15T20:00:00Z")

private val GOLDEN_GUIDE_CHANNELS =
    listOf(
        "1" to "Example One",
        "2" to "Example Two",
        "5" to "News Channel",
        "7" to "Harbor Sports",
        "12" to "Quiet Signal TV",
        "13" to "Late Tide",
        "21" to "Copper Movies",
    ).mapIndexed { index, (number, name) -> LiveTvChannel("ch$index", number, name, imageTag = null) }

/** Per channel: title, then minutes after 20:00 where it ends; every program starts when the one before ends. */
private val GOLDEN_GUIDE_SCHEDULES =
    listOf(
        listOf("Evening News" to 30, "Northern Lights" to 90, "Paper Moon" to 150, "The Last Horizon" to 240),
        listOf("Harbor Lights" to 25, "A Remarkably Long Program Title That Cannot Fit" to 110, "Slow Fire" to 200),
        listOf("Weather" to 10, "Late Edition" to 40, "Open Water" to 100, "Midnight Express" to 160, "Silver" to 260),
        listOf("Live Football: Harbor vs. Copper" to 135, "Match Review" to 200),
        listOf("Winter Garden" to 60, "Second Sunrise" to 120, "Iron Orchard" to 190),
        emptyList(),
        listOf("Blue Meridian" to 95, "Glass Harbor" to 215),
    )

private fun goldenGuidePrograms(): Map<String, List<LiveTvProgram>> =
    GOLDEN_GUIDE_CHANNELS.zip(GOLDEN_GUIDE_SCHEDULES).associate { (channel, schedule) ->
        var from = -30
        channel.id to
            schedule.mapIndexed { index, (title, until) ->
                LiveTvProgram(
                    id = "${channel.id}-$index",
                    channelId = channel.id,
                    title = title,
                    subtitle = null,
                    overview = null,
                    start = GOLDEN_GUIDE_ORIGIN + from.minutes,
                    end = GOLDEN_GUIDE_ORIGIN + until.minutes,
                ).also { from = until }
            }
    }

/** The Live TV screen in guide mode: seven channels, the next three hours, one channel without a guide. */
@Composable
internal fun GoldenLiveTvGuide(strings: TvStrings) {
    val state =
        remember {
            TvLiveTvState.Ready(
                channels = GOLDEN_GUIDE_CHANNELS,
                guide = goldenGuidePrograms(),
                at = Instant.fromEpochMilliseconds(GOLDEN_GUIDE_NOW_MILLIS),
                guideUntil = GOLDEN_GUIDE_CHANNELS.associate { it.id to GOLDEN_GUIDE_ORIGIN + 6.hours },
            )
        }
    val context =
        remember(strings) {
            TvLiveTvContext(
                images = TvLibraryGridImages(null, null),
                routeKey = "golden-live-tv",
                strings = strings,
                focusMemory = TvFocusMemory(),
            )
        }
    CompositionLocalProvider(
        LocalTvNowMillis provides { GOLDEN_GUIDE_NOW_MILLIS },
        LocalTvGuideTimeZone provides TimeZone.getTimeZone("UTC"),
    ) {
        TvLiveTvScreen(
            state = state,
            context = context,
            actions = TvLiveTvActions(onPlay = {}, onRetry = {}, onVisibleChannels = {}),
            guideMode = true,
        )
    }
}
