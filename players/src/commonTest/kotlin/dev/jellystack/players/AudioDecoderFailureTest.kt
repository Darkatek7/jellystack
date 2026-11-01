package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioDecoderFailureTest {
    @Test
    fun platformAndFfmpegAudioRendererFailuresAreAudioDecoderFailures() {
        assertTrue(IllegalStateException("MediaCodecAudioRenderer error, index=1").isAudioDecoderFailure())
        assertTrue(IllegalStateException("FfmpegAudioRenderer error, index=1, format=null").isAudioDecoderFailure())
    }

    @Test
    fun ffmpegDecoderExceptionAnywhereInTheCauseChainCountsAsAudioDecoderFailure() {
        class FfmpegDecoderException(
            message: String,
        ) : Exception(message)

        assertTrue(IllegalStateException("Source error", FfmpegDecoderException("Decode error")).isAudioDecoderFailure())
    }

    @Test
    fun videoAndUnrelatedFailuresAreNotAudioDecoderFailures() {
        assertFalse(IllegalStateException("MediaCodecVideoRenderer error, index=0").isAudioDecoderFailure())
        assertFalse(IllegalStateException("Unable to connect").isAudioDecoderFailure())
        assertFalse(null.isAudioDecoderFailure())
    }
}
