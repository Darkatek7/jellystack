package dev.jellystack.core.jellyfin

data class JellyfinEnvironment(
    val serverKey: String,
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val deviceId: String?,
    val deviceName: String,
)

fun interface JellyfinEnvironmentProvider {
    suspend fun current(): JellyfinEnvironment?
}
