package app.jellystack.mobile.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.jellystack.players.PlaybackNetworkClass
import dev.jellystack.players.PlaybackNetworkClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidNetworkClassifier(
    context: Context,
) : PlaybackNetworkClassifier {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mutableNetworkClass = MutableStateFlow(readNetworkClass())
    val networkClass: StateFlow<PlaybackNetworkClass> = mutableNetworkClass.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh()
        }

    init {
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
    }

    override fun currentNetworkClass(): PlaybackNetworkClass = mutableNetworkClass.value

    fun release() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun refresh() {
        mutableNetworkClass.value = readNetworkClass()
    }

    private fun readNetworkClass(): PlaybackNetworkClass {
        val capabilities = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        return when {
            capabilities == null -> PlaybackNetworkClass.UNKNOWN
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> PlaybackNetworkClass.UNMETERED
            else -> PlaybackNetworkClass.METERED
        }
    }
}
