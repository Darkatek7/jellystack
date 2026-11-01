package dev.jellystack.core.server

enum class ServerType {
    JELLYFIN,
    SONARR,
    RADARR,
    JELLYSEERR,
    MEDIARIUM,
    ;

    companion object {
        fun fromStored(value: String): ServerType = valueOf(value.uppercase())
    }
}

val ServerType.isRequestProvider: Boolean
    get() = this == ServerType.JELLYSEERR || this == ServerType.MEDIARIUM
