@file:Suppress("FunctionName")

package dev.jellystack.design.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import dev.jellystack.design.preview.JellystackPreviewFixture

private const val PHONE_PORTRAIT = "spec:width=411dp,height=891dp,dpi=420"
private const val PHONE_LANDSCAPE = "spec:width=891dp,height=411dp,dpi=420"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

@Preview(name = "Phone portrait", device = PHONE_PORTRAIT, showSystemUi = true)
@Preview(name = "Short phone landscape", device = PHONE_LANDSCAPE, showSystemUi = true)
@Preview(name = "Expanded tablet", device = TABLET_LANDSCAPE, showSystemUi = true)
private annotation class ResponsiveLayouts

@Composable
private fun fixture(
    name: String,
    dark: Boolean,
) = JellystackPreviewFixture(name, dark)

@PreviewTest @ResponsiveLayouts @Composable
fun HomeLight() = fixture("home", false)

@PreviewTest @ResponsiveLayouts @Composable
fun HomeDark() = fixture("home", true)

@PreviewTest @ResponsiveLayouts @Composable
fun LibraryLight() = fixture("library", false)

@PreviewTest @ResponsiveLayouts @Composable
fun LibraryDark() = fixture("library", true)

@PreviewTest @ResponsiveLayouts @Composable
fun DiscoverLight() = fixture("discover", false)

@PreviewTest @ResponsiveLayouts @Composable
fun DiscoverDark() = fixture("discover", true)

@PreviewTest @ResponsiveLayouts @Composable
fun RequestsLight() = fixture("requests", false)

@PreviewTest @ResponsiveLayouts @Composable
fun RequestsDark() = fixture("requests", true)

@PreviewTest @ResponsiveLayouts @Composable
fun SettingsLight() = fixture("settings", false)

@PreviewTest @ResponsiveLayouts @Composable
fun SettingsDark() = fixture("settings", true)

@PreviewTest @ResponsiveLayouts @Composable
fun OnboardingLight() = fixture("onboarding", false)

@PreviewTest @ResponsiveLayouts @Composable
fun OnboardingDark() = fixture("onboarding", true)

@PreviewTest @ResponsiveLayouts @Composable
fun DetailLight() = fixture("detail", false)

@PreviewTest @ResponsiveLayouts @Composable
fun DetailDark() = fixture("detail", true)

@PreviewTest
@Preview(name = "German 200% Home", device = PHONE_PORTRAIT, showSystemUi = true, locale = "de", fontScale = 2f)
@Composable
fun GermanLargeHome() = fixture("home", false)

@PreviewTest
@Preview(name = "German 200% Requests", device = PHONE_PORTRAIT, showSystemUi = true, locale = "de", fontScale = 2f)
@Composable
fun GermanLargeRequests() = fixture("requests", false)

@PreviewTest
@Preview(name = "German 200% Settings", device = TABLET_LANDSCAPE, showSystemUi = true, locale = "de", fontScale = 2f)
@Composable
fun GermanLargeSettings() = fixture("settings", false)

@PreviewTest
@Preview(name = "German 200% Onboarding", device = PHONE_PORTRAIT, showSystemUi = true, locale = "de", fontScale = 2f)
@Composable
fun GermanLargeOnboarding() = fixture("onboarding", false)
