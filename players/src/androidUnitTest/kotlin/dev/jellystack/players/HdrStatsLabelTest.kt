package dev.jellystack.players

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HdrStatsLabelTest {
    @Test
    fun dolbyVisionTrackIsLabelledWithItsProfileWhateverTheTransferFunctionSays() {
        assertEquals("Dolby Vision P5", hdrStatsLabel(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.05.06", null))
        assertEquals("Dolby Vision P8", hdrStatsLabel(MimeTypes.VIDEO_DOLBY_VISION, "dvh1.08.07", C.COLOR_TRANSFER_ST2084))
        assertEquals("Dolby Vision", hdrStatsLabel(MimeTypes.VIDEO_DOLBY_VISION, null, C.COLOR_TRANSFER_ST2084))
    }

    @Test
    fun plainHdrStreamsKeepTheTransferFunctionLabels() {
        assertEquals("HDR10/PQ", hdrStatsLabel(MimeTypes.VIDEO_H265, "hvc1.2.4.L153", C.COLOR_TRANSFER_ST2084))
        assertEquals("HLG", hdrStatsLabel(MimeTypes.VIDEO_H265, null, C.COLOR_TRANSFER_HLG))
        assertNull(hdrStatsLabel(MimeTypes.VIDEO_H264, null, C.COLOR_TRANSFER_SDR))
    }
}
