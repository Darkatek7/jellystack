package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioTrackSwitchPolicyTest {
    @Test
    fun hlsAlwaysNeedsANewServerStreamForTheTrack() {
        assertEquals(true, shouldResolveAudioTrackOnServer(PlaybackMode.HLS, isRemoteConnected = false, canPlayTrackLocally = true))
    }

    @Test
    fun directPlaySwitchesLocallyOnlyWhenTheDeviceCanDecodeTheTrack() {
        assertEquals(false, shouldResolveAudioTrackOnServer(PlaybackMode.DIRECT, isRemoteConnected = false, canPlayTrackLocally = true))
        assertEquals(true, shouldResolveAudioTrackOnServer(PlaybackMode.DIRECT, isRemoteConnected = false, canPlayTrackLocally = false))
    }

    @Test
    fun remoteSessionsAndDownloadsNeverResolveANewStream() {
        assertEquals(false, shouldResolveAudioTrackOnServer(PlaybackMode.HLS, isRemoteConnected = true, canPlayTrackLocally = false))
        assertEquals(false, shouldResolveAudioTrackOnServer(PlaybackMode.DIRECT, isRemoteConnected = true, canPlayTrackLocally = false))
        assertEquals(false, shouldResolveAudioTrackOnServer(PlaybackMode.LOCAL, isRemoteConnected = false, canPlayTrackLocally = false))
    }
}
