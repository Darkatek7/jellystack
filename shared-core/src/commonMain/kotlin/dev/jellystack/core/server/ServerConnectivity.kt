package dev.jellystack.core.server

fun interface ServerConnectivity {
    suspend fun test(registration: ServerRegistration): ConnectivityResult
}
