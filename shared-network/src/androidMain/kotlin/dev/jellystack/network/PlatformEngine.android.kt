package dev.jellystack.network

import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun providePlatformEngine(): HttpClientEngineFactory<out HttpClientEngineConfig> = OkHttp
