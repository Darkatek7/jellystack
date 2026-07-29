package dev.jellystack.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

data class ClientConfig(
    val engine: HttpClientEngine? = null,
    val engineFactory: HttpClientEngineFactory<out HttpClientEngineConfig>? = null,
    val requestTimeoutMillis: Long = 15_000,
    val connectTimeoutMillis: Long = 10_000,
    val socketTimeoutMillis: Long = 15_000,
    val maxRetries: Int = 2,
    val retryDelayMillis: Long = 400,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val userAgent: String = "JellystackMobile/0.1",
    val installLogging: Boolean = false,
    val logger: Logger = StdoutLogger,
    val logLevel: LogLevel = LogLevel.INFO,
    val json: Json = NetworkJson.default,
    val configure: HttpClientConfig<*>.() -> Unit = {},
)

object NetworkClientFactory {
    fun create(config: ClientConfig = ClientConfig()): HttpClient {
        val block: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(config.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = config.requestTimeoutMillis
                connectTimeoutMillis = config.connectTimeoutMillis
                socketTimeoutMillis = config.socketTimeoutMillis
            }
            install(HttpRequestRetry) {
                maxRetries = config.maxRetries
                retryIf { _, response -> !response.status.isSuccess() && response.status.value >= 500 }
                retryOnExceptionIf { _, cause -> cause is IOException }
                delayMillis { attempt ->
                    val factor = (attempt + 1).coerceAtMost(4)
                    config.retryDelayMillis * factor
                }
            }
            install(DefaultRequest) {
                if (headers[HttpHeaders.Accept] == null) {
                    headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
                if (headers[HttpHeaders.UserAgent] == null) {
                    headers.append(HttpHeaders.UserAgent, config.userAgent)
                }
                config.defaultHeaders.forEach { (key, value) ->
                    if (headers[key] == null) {
                        headers.append(key, value)
                    }
                }
            }
            if (config.installLogging) {
                install(Logging) {
                    logger = config.logger
                    level = config.logLevel
                }
            }
            config.configure(this)
        }

        return when {
            config.engine != null -> HttpClient(config.engine) { block() }
            config.engineFactory != null -> HttpClient(config.engineFactory) { block() }
            else -> HttpClient(providePlatformEngine()) { block() }
        }
    }
}

private object StdoutLogger : Logger {
    override fun log(message: String) {
        println("[Network] $message")
    }
}
