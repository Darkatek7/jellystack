package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlaybackErrorCopyTest {
    @Test
    fun rawDecoderDetailsAreNeverShownAsThePrimaryTvErrorMessage() {
        val strings = TvStrings.current(dev.jellystack.core.preferences.AppLanguage.ENGLISH)

        assertEquals(
            strings.playbackFailedMessage,
            tvPlaybackErrorMessage(
                "MediaCodecAudioRenderer error, index=1, format=Format(audio/mp4a-latm)",
                strings,
            ),
        )
    }
}
