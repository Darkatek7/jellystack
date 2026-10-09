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
fun TvMediaCards() = JellystackTvGoldenFixture(TvGoldenFixture.MEDIA_CARDS)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvMediaCardsGermanLarge() = JellystackTvGoldenFixture(TvGoldenFixture.MEDIA_CARDS, AppLanguage.GERMAN)
