package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.playback.StreamingPlayStrategy
import dev.jellystack.core.playback.StreamingProgressContext
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranscodeStopReporterTest {
    private val environment =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://jellyfin.example.com",
            accessToken = "token",
            userId = "user",
            deviceId = "device",
            deviceName = "TV",
        )

    @Test
    fun stoppingATranscodedSessionStopsItsEncodingOnly() =
        runTest {
            val service = RecordingInfoService()
            val reporter = TranscodeStopReporter(service) { environment }

            reporter.onStart(context(StreamingPlayStrategy.TRANSCODED), positionMs = 0L)
            reporter.onProgress(context(StreamingPlayStrategy.TRANSCODED), positionMs = 5_000L)
            assertEquals(emptyList(), service.stopped)

            reporter.onStop(context(StreamingPlayStrategy.TRANSCODED, playSessionId = "play-1"), positionMs = null, completed = false)

            assertEquals(listOf("play-1"), service.stopped)
        }

    @Test
    fun directSessionsAndSessionsWithoutAnIdStartNoEncodingStop() =
        runTest {
            val service = RecordingInfoService()
            val reporter = TranscodeStopReporter(service) { environment }

            reporter.onStop(context(StreamingPlayStrategy.DIRECT), positionMs = null, completed = false)
            reporter.onStop(context(StreamingPlayStrategy.TRANSCODED, playSessionId = null), positionMs = null, completed = true)

            assertEquals(emptyList(), service.stopped)
        }

    @Test
    fun withoutAnEnvironmentNothingIsSent() =
        runTest {
            val service = RecordingInfoService()
            val reporter = TranscodeStopReporter(service, JellyfinEnvironmentProvider { null })

            reporter.onStop(context(StreamingPlayStrategy.TRANSCODED), positionMs = null, completed = false)

            assertEquals(emptyList(), service.stopped)
        }

    @Test
    fun serverFailuresAreSwallowedButCancellationIsNot() =
        runTest {
            val failing = TranscodeStopReporter(RecordingInfoService(failure = IllegalStateException("offline"))) { environment }
            failing.onStop(context(StreamingPlayStrategy.TRANSCODED), positionMs = null, completed = false)

            val cancelled = TranscodeStopReporter(RecordingInfoService(failure = CancellationException("cancelled"))) { environment }
            assertFailsWith<CancellationException> {
                cancelled.onStop(context(StreamingPlayStrategy.TRANSCODED), positionMs = null, completed = false)
            }
        }

    private fun context(
        strategy: StreamingPlayStrategy,
        playSessionId: String? = "play-session",
    ) = StreamingProgressContext(
        mediaId = "item",
        mediaSourceId = "source",
        playSessionId = playSessionId,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        strategy = strategy,
    )

    private class RecordingInfoService(
        private val failure: Throwable? = null,
    ) : JellyfinPlaybackInfoService {
        val stopped = mutableListOf<String>()

        override suspend fun fetch(
            environment: JellyfinEnvironment,
            itemId: String,
            userId: String,
            request: JellyfinPlaybackInfoRequestDto,
        ): JellyfinPlaybackInfoResponseDto = error("Playback info is not needed")

        override suspend fun stopEncoding(
            environment: JellyfinEnvironment,
            playSessionId: String,
        ) {
            failure?.let { throw it }
            stopped += playSessionId
        }
    }
}
