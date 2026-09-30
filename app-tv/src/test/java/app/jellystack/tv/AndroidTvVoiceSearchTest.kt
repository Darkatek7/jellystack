package app.jellystack.tv

import dev.jellystack.design.tv.TvVoiceSearchAvailability
import dev.jellystack.design.tv.TvVoiceSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AndroidTvVoiceSearchTest {
    @Test
    fun unavailableRecognizerNeverLaunches() {
        var launches = 0
        val port = AndroidTvVoiceSearch.forTest(recognizerAvailable = false) { launches += 1 }

        port.launch { }

        assertEquals(TvVoiceSearchAvailability.UNAVAILABLE, port.availability)
        assertEquals(0, launches)
    }

    @Test
    fun resultMappingDistinguishesSuccessCancellationAndError() {
        assertEquals(TvVoiceSearchResult.Success("Dune"), mapVoiceSearchResult(true, listOf(" Dune ")))
        assertIs<TvVoiceSearchResult.Cancelled>(mapVoiceSearchResult(false, emptyList()))
        assertIs<TvVoiceSearchResult.Error>(mapVoiceSearchResult(true, emptyList()))
    }
}
