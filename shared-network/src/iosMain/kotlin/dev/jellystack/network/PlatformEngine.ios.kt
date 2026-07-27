package dev.jellystack.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun providePlatformEngine(): HttpClientEngineFactory<out HttpClientEngineConfig> = Darwin
