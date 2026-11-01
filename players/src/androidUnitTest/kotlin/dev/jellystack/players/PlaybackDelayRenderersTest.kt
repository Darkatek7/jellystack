package dev.jellystack.players

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@UnstableApi
class PlaybackDelayRenderersTest {
    @Test
    fun audioDelayMovesTheReportedPositionLaterSoThePictureRunsEarlier() {
        val delays = PlaybackDelays().apply { setAudioMs(150) }
        val sink = DelayedAudioSink(audioSinkAt(position = 2_000_000L), delays)

        assertEquals(2_150_000L, sink.getCurrentPositionUs(false))
    }

    @Test
    fun negativeAudioDelayMovesTheReportedPositionEarlier() {
        val delays = PlaybackDelays().apply { setAudioMs(-300) }
        val sink = DelayedAudioSink(audioSinkAt(position = 2_000_000L), delays)

        assertEquals(1_700_000L, sink.getCurrentPositionUs(false))
    }

    @Test
    fun unsetAudioPositionPassesThroughUnchanged() {
        val delays = PlaybackDelays().apply { setAudioMs(5_000) }
        val sink = DelayedAudioSink(audioSinkAt(position = AudioSink.CURRENT_POSITION_NOT_SET), delays)

        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, sink.getCurrentPositionUs(false))
    }

    @Test
    fun audioDelayChangeAppliesToTheNextPositionWithoutRebuildingTheSink() {
        val delays = PlaybackDelays()
        val sink = DelayedAudioSink(audioSinkAt(position = 1_000_000L), delays)

        assertEquals(1_000_000L, sink.getCurrentPositionUs(false))
        delays.setAudioMs(50)
        assertEquals(1_050_000L, sink.getCurrentPositionUs(false))
    }

    @Test
    fun sourceEndedFlagReachesTheWrappedSink() {
        val seen = mutableListOf<Boolean>()
        val sink =
            DelayedAudioSink(
                audioSink { method, args ->
                    if (method == "getCurrentPositionUs") {
                        seen += args!![0] as Boolean
                        0L
                    } else {
                        null
                    }
                },
                PlaybackDelays(),
            )

        sink.getCurrentPositionUs(true)

        assertEquals(listOf(true), seen)
    }

    @Test
    fun subtitleDelayRendersTheWrappedRendererAtAnEarlierPosition() {
        val rendered = mutableListOf<Long>()
        val delays = PlaybackDelays().apply { setSubtitleMs(500) }
        val renderer = DelayedTextRenderer(recordingRenderer(rendered), delays)

        renderer.render(10_000_000L, 0L)

        assertEquals(listOf(9_500_000L), rendered)
    }

    @Test
    fun negativeSubtitleDelayRendersTheWrappedRendererAtALaterPosition() {
        val rendered = mutableListOf<Long>()
        val delays = PlaybackDelays().apply { setSubtitleMs(-2_000) }
        val renderer = DelayedTextRenderer(recordingRenderer(rendered), delays)

        renderer.render(10_000_000L, 0L)

        assertEquals(listOf(12_000_000L), rendered)
    }

    @Test
    fun raisingTheSubtitleDelayHoldsTheRenderedPositionInsteadOfRewindingTheCues() {
        val rendered = mutableListOf<Long>()
        val delays = PlaybackDelays()
        val renderer = DelayedTextRenderer(recordingRenderer(rendered), delays)

        renderer.render(10_000_000L, 0L)
        delays.setSubtitleMs(100)
        renderer.render(10_020_000L, 0L)
        renderer.render(10_150_000L, 0L)

        assertEquals(listOf(10_000_000L, 10_000_000L, 10_050_000L), rendered)
    }

    @Test
    fun seekingBackwardsRendersAtTheNewPositionAgain() {
        val rendered = mutableListOf<Long>()
        val delays = PlaybackDelays().apply { setSubtitleMs(100) }
        val renderer = DelayedTextRenderer(recordingRenderer(rendered), delays)

        renderer.render(60_000_000L, 0L)
        renderer.resetPosition(5_000_000L, true)
        renderer.render(5_000_000L, 0L)

        assertEquals(listOf(59_900_000L, 4_900_000L), rendered)
    }

    @Test
    fun delaysAreClampedToTheSupportedRange() {
        val delays = PlaybackDelays()

        delays.setAudioMs(60_000)
        delays.setSubtitleMs(-60_000)

        assertEquals(5_000_000L, delays.audioUs)
        assertEquals(-5_000_000L, delays.subtitleUs)
    }

    private fun audioSinkAt(position: Long): AudioSink = audioSink { method, _ -> if (method == "getCurrentPositionUs") position else null }

    private fun audioSink(handler: (method: String, args: Array<Any?>?) -> Any?): AudioSink =
        Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            handler(method.name, args) ?: defaultValue(method.returnType)
        } as AudioSink

    private fun recordingRenderer(rendered: MutableList<Long>): Renderer =
        Proxy.newProxyInstance(Renderer::class.java.classLoader, arrayOf(Renderer::class.java)) { _, method, args ->
            if (method.name == "render") rendered += args!![0] as Long
            defaultValue(method.returnType)
        } as Renderer

    private fun defaultValue(type: Class<*>): Any? =
        when (type) {
            java.lang.Long.TYPE -> 0L
            java.lang.Integer.TYPE -> 0
            java.lang.Boolean.TYPE -> false
            java.lang.Float.TYPE -> 0f
            else -> null
        }
}
