package dev.jellystack.players.cast

import dev.jellystack.players.cast.CastConnectionState.Connected
import dev.jellystack.players.cast.CastConnectionState.Connecting
import dev.jellystack.players.cast.CastConnectionState.Error
import dev.jellystack.players.cast.CastConnectionState.Idle
import dev.jellystack.players.cast.testing.CastSessionManagerFake
import dev.jellystack.players.cast.testing.CastSessionManagerFake.Command
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CastSessionManagerFakeTest {
    private val sampleSnapshot =
        CastSessionSnapshot(
            mediaId = "item-1",
            title = "Episode",
            seriesName = "Show",
            episodeName = "Episode 1",
            artworkUrl = "https://example.com/poster.jpg",
            streamUrl = "https://example.com/stream.m3u8",
            positionMs = 42_000L,
            durationMs = 180_000L,
            isPaused = false,
        )

    @Test
    fun connectionStateTransitionsAreReplayed() =
        runTest {
            val fake = CastSessionManagerFake()
            val states = mutableListOf<CastConnectionState>()

            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    fake.connectionState
                        .take(6)
                        .collect { states += it }
                }

            fake.connecting("Living Room TV")
            fake.connected("Living Room TV", sampleSnapshot)
            fake.drop()
            fake.reconnect()

            collectJob.join()

            val expected =
                listOf(
                    Idle,
                    Connecting("Living Room TV"),
                    Connected("Living Room TV", sampleSnapshot),
                    Error(null),
                    Connecting("Living Room TV"),
                    Connected("Living Room TV", sampleSnapshot),
                )
            assertEquals(expected, states)
        }

    @Test
    fun castCommandsAreRecordedInOrder() =
        runTest {
            val fake = CastSessionManagerFake()

            fake.play()
            fake.pause()
            fake.seek(120_000L)
            fake.stop()
            fake.disconnect()

            assertEquals(
                listOf(
                    Command.Play,
                    Command.Pause,
                    Command.Seek(positionMs = 120_000L),
                    Command.Stop,
                    Command.Disconnect,
                ),
                fake.commands,
            )
        }

    @Test
    fun reconnectFallbackUsesLastSnapshot() =
        runTest {
            val fake = CastSessionManagerFake()
            val states = mutableListOf<CastConnectionState>()
            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    fake.connectionState
                        .take(5)
                        .collect { states += it }
                }

            fake.connected("Bedroom TV", sampleSnapshot)
            fake.drop()
            val reconnected = fake.reconnect()

            collectJob.join()

            assertTrue(reconnected, "Expected reconnect() to reuse previous snapshot")
            assertEquals(
                listOf(
                    Connected("Bedroom TV", sampleSnapshot),
                    Error(null),
                    Connecting("Bedroom TV"),
                    Connected("Bedroom TV", sampleSnapshot),
                ),
                states.drop(1),
            )
        }

    @Test
    fun remoteProgressEmitsPositions() =
        runTest {
            val fake = CastSessionManagerFake()
            val progress = mutableListOf<Long>()
            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    fake.remoteProgress
                        .take(2)
                        .collect { progress += it }
                }

            fake.emitProgress(15_000L)
            fake.emitProgress(47_500L)

            collectJob.join()
            assertEquals(listOf(15_000L, 47_500L), progress)
        }
}
