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
