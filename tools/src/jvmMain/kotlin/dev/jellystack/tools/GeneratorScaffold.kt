package dev.jellystack.tools

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name

private data class PropertySpec(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val description: String? = null,
    val serialName: String? = null,
)

private data class ModelSpec(
    val name: String,
    val description: String,
    val properties: List<PropertySpec>,
)

private enum class HttpMethod(
    val value: String,
) {
    GET("HttpMethod.Get"),
    POST("HttpMethod.Post"),
}

private data class OperationSpec(
    val functionName: String,
    val description: String,
    val method: HttpMethod,
    val path: String,
    val requestType: String?,
    val responseType: String?,
)

private data class ServiceSpec(
    val id: String,
    val packageName: String,
    val clientClassName: String,
    val description: String,
    val basePath: String,
    val authHeader: String?,
    val models: List<ModelSpec>,
    val operations: List<OperationSpec>,
)

private val serviceSpecs =
    listOf(
        ServiceSpec(
            id = "jellyfin",
            packageName = "dev.jellystack.network.generated.jellyfin",
            clientClassName = "JellyfinAuthApi",
            description = "Authentication client covering Jellyfin credential login flow.",
            basePath = "",
            authHeader = null,
            models =
                listOf(
                    ModelSpec(
                        name = "AuthenticateByNameRequest",
                        description = "Credentials payload for the Jellyfin authenticate-by-name endpoint.",
                        properties =
                            listOf(
                                PropertySpec("username", "String", serialName = "Username"),
                                PropertySpec(
                                    name = "password",
                                    type = "String",
                                    description = "Plain text password for authentication.",
                                    serialName = "Pw",
                                ),
                                PropertySpec(
                                    name = "legacyPasswordHash",
                                    type = "String",
                                    nullable = true,
                                    description = "Optional SHA1 password hash supported by legacy Jellyfin builds.",
                                    serialName = "Password",
                                ),
                                PropertySpec("deviceId", "String", nullable = true, serialName = "DeviceId"),
                            ),
                    ),
                    ModelSpec(
                        name = "AuthenticateByNameUser",
                        description = "Minimal user payload nested within login response.",
                        properties =
                            listOf(
                                PropertySpec("id", "String", serialName = "Id"),
                                PropertySpec("name", "String", nullable = true, serialName = "Name"),
                            ),
                    ),
                    ModelSpec(
                        name = "AuthenticateByNameResponse",
                        description = "Subset of Jellyfin auth response needed for onboarding.",
                        properties =
                            listOf(
                                PropertySpec("accessToken", "String", serialName = "AccessToken"),
                                PropertySpec("user", "AuthenticateByNameUser", serialName = "User"),
                                PropertySpec("serverId", "String", nullable = true, serialName = "ServerId"),
                            ),
                    ),
                ),
            operations =
                listOf(
                    OperationSpec(
                        functionName = "authenticateByName",
                        description = "Authenticate a user by username/password and return access token.",
                        method = HttpMethod.POST,
                        path = "/Users/AuthenticateByName",
                        requestType = "AuthenticateByNameRequest",
                        responseType = "AuthenticateByNameResponse",
                    ),
                ),
        ),
        ServiceSpec(
            id = "sonarr",
            packageName = "dev.jellystack.network.generated.sonarr",
            clientClassName = "SonarrSystemApi",
            description = "System status client for Sonarr onboarding connectivity checks.",
            basePath = "",
            authHeader = "X-Api-Key",
            models =
                listOf(
                    ModelSpec(
                        name = "SystemStatusResponse",
                        description = "Subset of Sonarr system status useful for diagnostics.",
                        properties =
                            listOf(
                                PropertySpec("appName", "String"),
                                PropertySpec("version", "String"),
                                PropertySpec("releaseBranch", "String", nullable = true),
                            ),
                    ),
                ),
            operations =
                listOf(
                    OperationSpec(
                        functionName = "fetchSystemStatus",
                        description = "Retrieve system status meta data to verify connectivity and auth.",
                        method = HttpMethod.GET,
                        path = "/api/v3/system/status",
                        requestType = null,
                        responseType = "SystemStatusResponse",
                    ),
                ),
        ),
        ServiceSpec(
            id = "radarr",
            packageName = "dev.jellystack.network.generated.radarr",
            clientClassName = "RadarrSystemApi",
            description = "System status client for Radarr onboarding connectivity checks.",
            basePath = "",
            authHeader = "X-Api-Key",
            models =
                listOf(
                    ModelSpec(
                        name = "SystemStatusResponse",
                        description = "Subset of Radarr system status useful for diagnostics.",
                        properties =
                            listOf(
                                PropertySpec("appName", "String"),
                                PropertySpec("version", "String"),
                                PropertySpec("releaseBranch", "String", nullable = true),
                            ),
                    ),
                ),
            operations =
                listOf(
                    OperationSpec(
                        functionName = "fetchSystemStatus",
                        description = "Retrieve system status meta data to verify connectivity and auth.",
                        method = HttpMethod.GET,
                        path = "/api/v3/system/status",
                        requestType = null,
                        responseType = "SystemStatusResponse",
                    ),
                ),
        ),
        ServiceSpec(
            id = "jellyseerr",
            packageName = "dev.jellystack.network.generated.jellyseerr",
            clientClassName = "JellyseerrStatusApi",
            description = "Status client for Jellyseerr onboarding connectivity checks.",
            basePath = "",
            authHeader = "X-Api-Key",
            models =
                listOf(
                    ModelSpec(
                        name = "StatusResponse",
                        description = "Minimal status payload confirming server identity.",
                        properties =
                            listOf(
                                PropertySpec("version", "String"),
                                PropertySpec("commitTag", "String", nullable = true),
                            ),
                    ),
                ),
            operations =
                listOf(
                    OperationSpec(
                        functionName = "fetchStatus",
                        description = "Retrieve Jellyseerr status for connectivity verification.",
                        method = HttpMethod.GET,
                        path = "/api/v1/status",
                        requestType = null,
                        responseType = "StatusResponse",
                    ),
                ),
        ),
    )

fun main(args: Array<String>) {
    val projectRoot =
        args
            .firstOrNull()
            ?.let { Paths.get(it) }
            ?.toAbsolutePath()
            ?.normalize()
            ?: Paths
                .get("..")
                .toAbsolutePath()
                .normalize()

    val outputDir =
        projectRoot.resolve(
            "shared-network/src/commonMain/kotlin/dev/jellystack/network/generated",
        )

    if (outputDir.exists()) {
        Files
            .walk(outputDir)
            .sorted(Comparator.reverseOrder())
            .forEach { path ->
                if (Files.isRegularFile(path) && path.extension == "kt") {
                    Files.deleteIfExists(path)
                } else if (path != outputDir) {
                    Files.deleteIfExists(path)
                }
            }
    }

    outputDir.createDirectories()

    serviceSpecs.forEach { service ->
        val serviceDir =
            outputDir.resolve(service.id)
        serviceDir.createDirectories()

        val fileName = "${service.clientClassName}.kt"
        val fileContents = service.renderFile()
        Files.writeString(serviceDir.resolve(fileName), fileContents)
        println("Generated ${serviceDir.resolve(fileName).toAbsolutePath()}")
    }
}

private fun ServiceSpec.renderFile(): String {
    val builder = StringBuilder()
    builder.appendLine("// Generated by :tools:generateApis. Do not edit manually.")
    builder.appendLine("package $packageName")
    builder.appendLine()
    val usesRequestBody = operations.any { it.requestType != null }
    val usesAuth = authHeader != null
    val usesSerialNames = models.any { model -> model.properties.any { it.serialName != null } }
    builder.appendLine("import io.ktor.client.HttpClient")
    builder.appendLine("import io.ktor.client.call.body")
    builder.appendLine("import io.ktor.client.request.request")
    if (usesAuth) {
        builder.appendLine("import io.ktor.client.request.header")
    }
    if (usesRequestBody) {
        builder.appendLine("import io.ktor.client.request.setBody")
        builder.appendLine("import io.ktor.http.ContentType")
        builder.appendLine("import io.ktor.http.contentType")
    }
    builder.appendLine("import io.ktor.http.HttpMethod")
    builder.appendLine("import io.ktor.http.path")
    builder.appendLine("import io.ktor.http.takeFrom")
    builder.appendLine("import kotlinx.serialization.Serializable")
    if (usesSerialNames) {
        builder.appendLine("import kotlinx.serialization.SerialName")
    }
    builder.appendLine()

    models.forEach { model ->
        builder.appendLine("/** ${model.description} */")
        builder.appendLine("@Serializable")
        builder.appendLine("data class ${model.name}(")
        builder.append(model.renderProperties())
        builder.appendLine(")")
        builder.appendLine()
    }

    builder.appendLine("/** $description */")
    builder.appendLine("class $clientClassName(")
    builder.appendLine("    private val client: HttpClient,")
    builder.appendLine("    private val baseUrl: String,")
    if (authHeader != null) {
        builder.appendLine("    private val apiKey: String?,")
    }
    builder.appendLine(") {")
    builder.appendLine("    private fun io.ktor.client.request.HttpRequestBuilder.configureUrl(pathSuffix: String) {")
    builder.appendLine("        url {")
    builder.appendLine("            takeFrom(baseUrl)")
    if (basePath.isNotBlank()) {
        builder.appendLine("            path(\"${basePath.trim('/')}\")")
    }
    builder.appendLine("            path(pathSuffix.trimStart('/'))")
    builder.appendLine("        }")
    builder.appendLine("    }")
    if (authHeader != null) {
        builder.appendLine()
        builder.appendLine("    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth() {")
        builder.appendLine("        if (!apiKey.isNullOrBlank()) {")
        builder.appendLine("            header(\"$authHeader\", apiKey)")
        builder.appendLine("        }")
        builder.appendLine("    }")
    }
    builder.appendLine()

    operations.forEach { operation ->
        builder.appendLine("    /** ${operation.description} */")
        builder.append("    suspend fun ${operation.functionName}(")
        if (operation.requestType != null) {
            builder.append("payload: ${operation.requestType}")
        }
        builder.appendLine("): ${operation.responseType ?: "Unit"} =")
        builder.appendLine("        client.request {")
        builder.appendLine("            method = ${operation.method.value}")
        builder.appendLine("            configureUrl(\"${operation.path}\")")
        if (operation.requestType != null) {
            builder.appendLine("            contentType(ContentType.Application.Json)")
            builder.appendLine("            setBody(payload)")
        }
        if (authHeader != null) {
            builder.appendLine("            applyAuth()")
        }
        val responseType = operation.responseType ?: "Unit"
        builder.appendLine("        }.body<$responseType>()")
        builder.appendLine()
    }
    builder.appendLine("}")

    return builder.toString()
}

private fun ModelSpec.renderProperties(): String =
    properties.joinToString(separator = "\n", postfix = "\n") { property ->
        val nullableMark = if (property.nullable) "?" else ""
        val defaultValue = if (property.nullable) " = null" else ""
        val doc = property.description?.let { " // $it" } ?: ""
        buildString {
            property.serialName?.let { serialName ->
                append("    @SerialName(\"")
                append(serialName)
                append("\")\n")
            }
            append("    val ${property.name}: ${property.type}$nullableMark$defaultValue,$doc")
        }
    }
