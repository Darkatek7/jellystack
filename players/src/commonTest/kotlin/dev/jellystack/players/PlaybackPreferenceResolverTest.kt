package dev.jellystack.players

import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackPreferenceResolverTest {
    @Test
    fun meteredNetworkUsesMobileQualityAndUnmeteredUsesWifiQuality() {
        val settings =
            AppSettings(
                wifiStreamingQuality = StreamingQualityPreference.MBPS_8_1080P,
                mobileStreamingQuality = StreamingQualityPreference.KBPS_720_480P,
            )

        assertEquals(
            StreamingQualityPreference.MBPS_8_1080P,
            PlaybackPreferenceResolver(settings, PlaybackNetworkClass.UNMETERED).streamingQuality,
        )
        assertEquals(
            StreamingQualityPreference.KBPS_720_480P,
            PlaybackPreferenceResolver(settings, PlaybackNetworkClass.METERED).streamingQuality,
        )
    }

    @Test
    fun preferredAudioMatchesTwoAndThreeLetterLanguageCodes() {
        val resolver = PlaybackPreferenceResolver(AppSettings(preferredAudioLanguage = "deu"))
        val tracks =
            listOf(
                audio("english", "eng", isDefault = true),
                audio("german", "de"),
            )

        assertEquals("german", resolver.selectAudioTrack(tracks)?.id)
    }

    @Test
    fun forcedSubtitleModeNeverSelectsOrdinaryDefaultSubtitle() {
        val resolver = PlaybackPreferenceResolver(AppSettings(subtitleMode = SubtitleMode.FORCED_ONLY))
        val tracks =
            listOf(
                subtitle("ordinary", "eng", isDefault = true),
                subtitle("forced", "deu", isForced = true),
            )

        assertEquals("forced", resolver.selectSubtitleTrack(tracks, audioLanguage = "eng")?.id)
    }

    @Test
    fun preferredWhenAudioDiffersLeavesSubtitlesOffForMatchingAudio() {
        val resolver =
            PlaybackPreferenceResolver(
                AppSettings(
                    preferredSubtitleLanguage = "deu",
                    subtitleMode = SubtitleMode.PREFERRED_WHEN_AUDIO_DIFFERS,
                ),
            )
        val tracks = listOf(subtitle("german", "ger", isDefault = true))

        assertNull(resolver.selectSubtitleTrack(tracks, audioLanguage = "de"))
        assertEquals("german", resolver.selectSubtitleTrack(tracks, audioLanguage = "eng")?.id)
    }

    @Test
    fun qualityOptionChoosesMatchingPersistedBitrate() {
        val resolver =
            PlaybackPreferenceResolver(
                AppSettings(wifiStreamingQuality = StreamingQualityPreference.MBPS_4_720P),
                PlaybackNetworkClass.UNMETERED,
            )
        val options =
            listOf(
                quality(PlaybackQualityOption.AUTO_ID, null, null, isAuto = true),
                quality("8", 8_000_000, 1080),
                quality("4", 4_000_000, 720),
            )

        assertEquals("4", resolver.selectQualityOption(options)?.id)
    }

    private fun audio(
        id: String,
        language: String,
        isDefault: Boolean = false,
    ) = AudioTrack(id, language, null, null, isDefault, null)

    private fun subtitle(
        id: String,
        language: String,
        isDefault: Boolean = false,
        isForced: Boolean = false,
    ) = SubtitleTrack(id, language, null, SubtitleFormat.SRT, isDefault, isForced, null)

    private fun quality(
        id: String,
        bitrate: Int?,
        height: Int?,
        isAuto: Boolean = false,
    ) = PlaybackQualityOption(id, "", PlaybackMode.HLS, "source", bitrate, height, isAuto)
}
