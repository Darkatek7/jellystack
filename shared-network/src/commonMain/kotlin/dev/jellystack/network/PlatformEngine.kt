package dev.jellystack.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun providePlatformEngine(): HttpClientEngineFactory<out HttpClientEngineConfig>
