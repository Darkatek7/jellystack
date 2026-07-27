package dev.jellystack.network

import kotlinx.serialization.json.Json

object NetworkJson {
    val default: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }
}
