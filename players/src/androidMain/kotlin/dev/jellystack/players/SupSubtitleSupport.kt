package dev.jellystack.players

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.pgs.PgsParser
import java.io.ByteArrayOutputStream

/**
 * Private sample mime type of a Jellyfin `.sup` file that was loaded as one single sample.
 *
 * Media3 only understands PGS the way Matroska stores it (one display set per sample, timing from
 * the container). A standalone `.sup` file prefixes every segment with a `PG` header carrying the
 * timestamp, so it needs this small decoder in front of [PgsParser].
 */
internal const val SUP_FILE_MIME_TYPE = "application/x-jellystack-sup"

/** One PGS display set: the segments from the composition segment up to and including the END segment. */
internal class SupDisplaySet(
    val timeUs: Long,
    /** Segments in the layout [PgsParser] expects: `type (1), length (2), payload` for each segment. */
    val sections: ByteArray,
)

/**
 * Splits the content of a `.sup` file into display sets. Parsing stops quietly at the first byte that
 * does not look like a segment header or at a truncated segment, because a partial subtitle track is
 * better than none and a subtitle problem must never fail playback.
 */
internal fun parseSupDisplaySets(
    data: ByteArray,
    length: Int,
): List<SupDisplaySet> {
    val sets = mutableListOf<SupDisplaySet>()
    var current = ByteArrayOutputStream()
    var currentTimeUs = 0L
    var position = 0
    while (length - position >= SUP_HEADER_SIZE) {
        if (data[position] != SUP_MAGIC_FIRST || data[position + 1] != SUP_MAGIC_SECOND) break
        val pts = readUnsignedInt(data, position + SUP_PTS_OFFSET)
        val type = data[position + SUP_TYPE_OFFSET].toInt() and BYTE_MASK
        val size =
            ((data[position + SUP_SIZE_OFFSET].toInt() and BYTE_MASK) shl 8) or (data[position + SUP_SIZE_OFFSET + 1].toInt() and BYTE_MASK)
        val payloadStart = position + SUP_HEADER_SIZE
        if (payloadStart + size > length) break
        if (type == SEGMENT_PRESENTATION_COMPOSITION) {
            // A composition segment starts a new display set; anything unfinished before it is unusable.
            current = ByteArrayOutputStream()
            currentTimeUs = pts * MICROS_PER_SECOND / PGS_CLOCK_HZ
        }
        current.write(type)
        current.write(size shr 8)
        current.write(size and BYTE_MASK)
        current.write(data, payloadStart, size)
        if (type == SEGMENT_END) {
            sets += SupDisplaySet(currentTimeUs, current.toByteArray())
            current = ByteArrayOutputStream()
        }
        position = payloadStart + size
    }
    val ordered = sets.sortedBy(SupDisplaySet::timeUs)
    // Two display sets at the same instant: the later one replaces the earlier one.
    return ordered.filterIndexed { index, set -> index == ordered.lastIndex || set.timeUs != ordered[index + 1].timeUs }
}

private fun readUnsignedInt(
    data: ByteArray,
    offset: Int,
): Long =
    ((data[offset].toLong() and BYTE_MASK_LONG) shl 24) or
        ((data[offset + 1].toLong() and BYTE_MASK_LONG) shl 16) or
        ((data[offset + 2].toLong() and BYTE_MASK_LONG) shl 8) or
        (data[offset + 3].toLong() and BYTE_MASK_LONG)

/**
 * A whole PGS track as a legacy [Subtitle]: one event per display set. The bitmaps are decoded on
 * demand and only the one currently shown is kept, so a feature film does not hold hundreds of
 * megabytes of decoded cue bitmaps.
 */
@UnstableApi
internal class SupSubtitle(
    private val displaySets: List<SupDisplaySet>,
) : Subtitle {
    private val parser = PgsParser()
    private var cachedIndex = NO_INDEX
    private var cachedCues: List<Cue> = emptyList()

    override fun getNextEventTimeIndex(timeUs: Long): Int {
        val index = firstIndexAfter(timeUs)
        return if (index < displaySets.size) index else INDEX_UNSET
    }

    override fun getEventTimeCount(): Int = displaySets.size

    override fun getEventTime(index: Int): Long = displaySets[index].timeUs

    override fun getCues(timeUs: Long): List<Cue> {
        val index = firstIndexAfter(timeUs) - 1
        if (index < 0) return emptyList()
        if (index != cachedIndex) {
            cachedCues = decode(displaySets[index])
            cachedIndex = index
        }
        return cachedCues
    }

    /** Index of the first display set that starts after [timeUs], or the set count when there is none. */
    private fun firstIndexAfter(timeUs: Long): Int {
        var low = 0
        var high = displaySets.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (displaySets[middle].timeUs > timeUs) high = middle else low = middle + 1
        }
        return low
    }

    private fun decode(set: SupDisplaySet): List<Cue> {
        var cues: List<Cue> = emptyList()
        parser.parse(
            set.sections,
            0,
            set.sections.size,
            SubtitleParser.OutputOptions.allCues(),
            Consumer<CuesWithTiming> { timing -> cues = timing.cues },
        )
        return cues
    }

    private companion object {
        private const val NO_INDEX = -1
        private const val INDEX_UNSET = -1
    }
}

@UnstableApi
internal class SupSubtitleDecoder : SimpleSubtitleDecoder("SupSubtitleDecoder") {
    override fun decode(
        data: ByteArray,
        length: Int,
        reset: Boolean,
    ): Subtitle = SupSubtitle(parseSupDisplaySets(data, length))
}

/** Default Media3 decoders plus [SupSubtitleDecoder] for sideloaded Jellyfin `.sup` files. */
@UnstableApi
internal object JellystackSubtitleDecoderFactory : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean =
        format.sampleMimeType == SUP_FILE_MIME_TYPE || SubtitleDecoderFactory.DEFAULT.supportsFormat(format)

    override fun createDecoder(format: Format): SubtitleDecoder =
        if (format.sampleMimeType == SUP_FILE_MIME_TYPE) {
            SupSubtitleDecoder()
        } else {
            SubtitleDecoderFactory.DEFAULT.createDecoder(format)
        }
}

/** The Media3 mime type a sideloaded subtitle of the given resolver mime type is loaded with. */
internal fun sideloadedSampleMimeType(resolvedMimeType: String): String =
    if (resolvedMimeType == MimeTypes.APPLICATION_PGS) SUP_FILE_MIME_TYPE else resolvedMimeType

private const val SUP_HEADER_SIZE = 13
private const val SUP_PTS_OFFSET = 2
private const val SUP_TYPE_OFFSET = 10
private const val SUP_SIZE_OFFSET = 11
private const val SUP_MAGIC_FIRST: Byte = 0x50
private const val SUP_MAGIC_SECOND: Byte = 0x47
private const val SEGMENT_PRESENTATION_COMPOSITION = 0x16
private const val SEGMENT_END = 0x80
private const val BYTE_MASK = 0xFF
private const val BYTE_MASK_LONG = 0xFFL
private const val PGS_CLOCK_HZ = 90_000L
private const val MICROS_PER_SECOND = 1_000_000L
