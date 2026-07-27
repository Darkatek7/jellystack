package dev.jellystack.core.server

import kotlinx.datetime.Instant

data class ServerRegistration(
    val id: String? = null,
    val type: ServerType,
    val name: String,
    val baseUrl: String,
    val credentials: CredentialInput,
)

data class AuthenticatedJellyfinRegistration(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val credentials: StoredCredential.Jellyfin,
)

data class ManagedServer(
    val id: String,
    val type: ServerType,
    val name: String,
    val baseUrl: String,
    val credentials: StoredCredential,
    val createdAt: Instant,
    val updatedAt: Instant,
)

sealed class ConnectivityResult {
    data class Success(
        val message: String,
        val credentials: StoredCredential,
    ) : ConnectivityResult()

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : ConnectivityResult()
}

class DuplicateServerException(
    val existingId: String,
    val type: ServerType,
    val baseUrl: String,
) : IllegalStateException(
        "Server already exists for ${type.name} at $baseUrl",
    )

class InvalidServerConfiguration(
    message: String,
) : IllegalArgumentException(message)

class ConnectivityException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
