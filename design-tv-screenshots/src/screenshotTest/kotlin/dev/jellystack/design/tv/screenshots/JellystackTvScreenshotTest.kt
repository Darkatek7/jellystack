@file:Suppress("FunctionName")

package dev.jellystack.design.tv.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.design.tv.JellystackTvGoldenFixture
import dev.jellystack.design.tv.TvGoldenFixture

private const val TV_720P = "spec:width=960dp,height=540dp,dpi=213"
private const val TV_1080P = "spec:width=960dp,height=540dp,dpi=320"
private const val TV_FULL_PAGE = "spec:width=960dp,height=1180dp,dpi=320"
private const val TV_4K = "spec:width=960dp,height=540dp,dpi=640"

@Preview(name = "720p", device = TV_720P)
@Preview(name = "1080p", device = TV_1080P)
@Preview(name = "4K", device = TV_4K)
private annotation class TvResolutionMatrix

@PreviewTest
@TvResolutionMatrix
@Composable
fun TvLandscapeCards() = JellystackTvGoldenFixture(TvGoldenFixture.LANDSCAPE_CARDS)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvLandscapeCardsGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.LANDSCAPE_CARDS, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvPortraitCards() = JellystackTvGoldenFixture(TvGoldenFixture.PORTRAIT_CARDS)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvPortraitCardsGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.PORTRAIT_CARDS, AppLanguage.GERMAN)

@PreviewTest
@TvResolutionMatrix
@Composable
fun TvPlayerControls() = JellystackTvGoldenFixture(TvGoldenFixture.PLAYER_CONTROLS)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvPlayerControlsGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.PLAYER_CONTROLS, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvPlayerScrub() = JellystackTvGoldenFixture(TvGoldenFixture.PLAYER_SCRUB)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvPlayerUpNext() = JellystackTvGoldenFixture(TvGoldenFixture.PLAYER_UP_NEXT)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvHomeHero() = JellystackTvGoldenFixture(TvGoldenFixture.HOME_HERO)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvHomeHeroGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.HOME_HERO, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvDetailHero() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_HERO)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvDetailHeroGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_HERO, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "German 1080p", device = TV_1080P, locale = "de")
@Composable
fun TvDetailHeroGerman() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_HERO, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "Full page", device = TV_FULL_PAGE)
@Composable
fun TvDetailMovieFullPage() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_HERO)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Preview(name = "Full page", device = TV_FULL_PAGE)
@Composable
fun TvDetailSeries() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_SERIES)

@PreviewTest
@Preview(name = "German 1080p", device = TV_1080P, locale = "de")
@Composable
fun TvDetailSeriesGerman() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_SERIES, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Preview(name = "Full page", device = TV_FULL_PAGE)
@Composable
fun TvDetailEpisode() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_EPISODE)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Preview(name = "Full page", device = TV_FULL_PAGE)
@Composable
fun TvDetailSeerr() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_SEERR)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvLibraryGrid() = JellystackTvGoldenFixture(TvGoldenFixture.LIBRARY_GRID)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvEmptyState() = JellystackTvGoldenFixture(TvGoldenFixture.EMPTY_STATE)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvEmptyStateGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.EMPTY_STATE, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvErrorState() = JellystackTvGoldenFixture(TvGoldenFixture.ERROR_STATE)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvSkeletonRows() = JellystackTvGoldenFixture(TvGoldenFixture.SKELETON_ROWS)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvDetailLoadError() = JellystackTvGoldenFixture(TvGoldenFixture.DETAIL_LOAD_ERROR)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvNavigationRailOpen() = JellystackTvGoldenFixture(TvGoldenFixture.NAVIGATION_RAIL_OPEN)

@PreviewTest
@Preview(name = "1080p", device = TV_1080P)
@Composable
fun TvResumeDialog() = JellystackTvGoldenFixture(TvGoldenFixture.RESUME_DIALOG)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvResumeDialogGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.RESUME_DIALOG, AppLanguage.GERMAN)
