package dev.jellystack.core.server

enum class ServerType {
    JELLYFIN,
    SONARR,
    RADARR,
    JELLYSEERR,
    ;

    companion object {
        fun fromStored(value: String): ServerType = valueOf(value.uppercase())
    }
}
