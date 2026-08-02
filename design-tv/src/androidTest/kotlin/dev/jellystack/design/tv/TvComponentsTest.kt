package dev.jellystack.design.tv

import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionButtonSupportsDpadAndTvMinimumTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            JellystackTvTheme {
                TvActionButton("Play", { clicks += 1 })
            }
        }

        composeRule
            .onNodeWithText("Play")
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun tvThemeProvidesReadableDefaultContentColor() {
        var observedColor = Color.Unspecified

        composeRule.setContent {
            JellystackTvTheme {
                observedColor = LocalContentColor.current
            }
        }

        composeRule.runOnIdle { assertEquals(TvText, observedColor) }
    }

    @Test
    fun sectionTitleRendersWithBrightForegroundPixels() {
        composeRule.setContent {
            JellystackTvTheme {
                TvSectionTitle("Section")
            }
        }

        assertHasBrightPixels(composeRule.onNodeWithText("Section").captureToImage(), "TV section title")
    }
}

private fun assertHasBrightPixels(
    image: ImageBitmap,
    label: String,
) {
    val pixels = image.toPixelMap()
    var brightPixelCount = 0
    for (y in 0 until pixels.height) {
        for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.alpha > 0.5f && color.red + color.green + color.blue > 2.4f) brightPixelCount++
        }
    }
    assertTrue("Expected $label to contain bright foreground pixels.", brightPixelCount >= 8)
}
