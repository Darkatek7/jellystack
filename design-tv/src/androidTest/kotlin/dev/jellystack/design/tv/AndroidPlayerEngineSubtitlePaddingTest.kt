package dev.jellystack.design.tv

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jellystack.players.AndroidPlayerEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlayerEngineSubtitlePaddingTest {
    @Test
    fun paddingSetBeforeSurfaceAttachmentIsClampedAndApplied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        instrumentation.runOnMainSync {
            val engine = AndroidPlayerEngine(context)
            try {
                engine.setSubtitleBottomPaddingFraction(-0.5f)
                val surface = engine.createVideoSurface(context)

                assertEquals(0f, surface.subtitleBottomPaddingFraction(), 0.0001f)
                engine.releaseVideoSurface(surface)
            } finally {
                engine.release()
            }
        }
    }

    @Test
    fun attachedSurfaceReceivesClampedUpdatesAndLaterSurfacesKeepTheValue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        instrumentation.runOnMainSync {
            val engine = AndroidPlayerEngine(context)
            try {
                val firstSurface = engine.createVideoSurface(context)
                engine.setSubtitleBottomPaddingFraction(1.5f)
                assertEquals(1f, firstSurface.subtitleBottomPaddingFraction(), 0.0001f)
                engine.setSubtitleBottomPaddingFraction(Float.NaN)
                assertEquals(0.08f, firstSurface.subtitleBottomPaddingFraction(), 0.0001f)
                engine.releaseVideoSurface(firstSurface)

                engine.setSubtitleBottomPaddingFraction(0.20f)
                val secondSurface = engine.createVideoSurface(context)
                assertEquals(0.20f, secondSurface.subtitleBottomPaddingFraction(), 0.0001f)
                engine.releaseVideoSurface(secondSurface)
            } finally {
                engine.release()
            }
        }
    }

    private fun View.subtitleBottomPaddingFraction(): Float {
        val subtitleView = requireNotNull(javaClass.getMethod("getSubtitleView").invoke(this))
        val field = subtitleView.javaClass.getDeclaredField("bottomPaddingFraction")
        field.isAccessible = true
        return field.getFloat(subtitleView)
    }
}
