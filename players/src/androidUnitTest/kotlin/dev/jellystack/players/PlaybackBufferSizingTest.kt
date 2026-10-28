package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackBufferSizingTest {
    private val mb = 1024 * 1024

    @Test
    fun smallHeapsGetAByteLimitBelowTheirHeap() {
        val bytes = PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 192, isLowRamDevice = false)

        assertEquals(76 * mb, bytes)
    }

    @Test
    fun lowRamDevicesGetASmallerShareOfTheSameHeap() {
        val normal = PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 192, isLowRamDevice = false)
        val lowRam = PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 192, isLowRamDevice = true)

        assertTrue(lowRam!! < normal!!)
        assertEquals(48 * mb, lowRam)
    }

    @Test
    fun theLimitNeverDropsBelowWhatAHighBitrateStartNeeds() {
        assertEquals(32 * mb, PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 64, isLowRamDevice = true))
        assertEquals(32 * mb, PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 0, isLowRamDevice = false))
    }

    @Test
    fun largeHeapsKeepMedia3Defaults() {
        assertNull(PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 512, isLowRamDevice = false))
        assertNull(PlaybackBufferSizing.targetBufferBytes(memoryClassMb = 384, isLowRamDevice = false))
    }
}
