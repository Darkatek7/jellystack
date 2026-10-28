package dev.jellystack.players

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

@UnstableApi
class ControllerRoutedPlayerTest {
    private val target = RecordingTarget()
    private val delegateCalls = mutableListOf<String>()
    private val player = ControllerRoutedPlayer(delegatePlayer(), target, seekBackMs = { 10_000L }, seekForwardMs = { 30_000L })

    @Test
    fun playAndPauseGoThroughTheController() {
        player.play()
        player.pause()
        player.playWhenReady = true
        player.playWhenReady = false

        assertEquals(listOf("resume", "pause", "resume", "pause"), target.calls)
        assertEquals(emptyList(), delegateCalls)
    }

    @Test
    fun stopAndSeeksGoThroughTheController() {
        player.stop()
        player.seekTo(12_000L)
        player.seekTo(3, 5_000L)
        player.seekToDefaultPosition()
        player.seekBack()
        player.seekForward()

        assertEquals(
            listOf("stop", "seekTo 12000", "seekTo 5000", "seekTo 0", "seekBy -10000", "seekBy 30000"),
            target.calls,
        )
        assertEquals(emptyList(), delegateCalls)
    }

    @Test
    fun relativeSeeksStayInsideTheItem() {
        assertEquals(0L, relativeSeekTarget(positionMs = 4_000L, durationMs = 60_000L, deltaMs = -10_000L))
        assertEquals(60_000L, relativeSeekTarget(positionMs = 50_000L, durationMs = 60_000L, deltaMs = 30_000L))
        assertEquals(80_000L, relativeSeekTarget(positionMs = 50_000L, durationMs = null, deltaMs = 30_000L))
    }

    private fun delegatePlayer(): Player {
        val handler =
            InvocationHandler { _, method, _ ->
                delegateCalls += method.name
                when (method.name) {
                    "getAvailableCommands" ->
                        Player.Commands
                            .Builder()
                            .addAllCommands()
                            .build()
                    else -> null
                }
            }
        return Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java), handler) as Player
    }

    private class RecordingTarget : PlaybackCommandTarget {
        val calls = mutableListOf<String>()

        override fun resume() {
            calls += "resume"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seekTo $positionMs"
        }

        override fun seekBy(deltaMs: Long) {
            calls += "seekBy $deltaMs"
        }
    }
}
