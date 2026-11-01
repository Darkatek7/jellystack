package dev.jellystack.design

import dev.jellystack.core.jellyseerr.RequestProviderType
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ServerAddressValidation
import dev.jellystack.core.server.validateServerAddress

internal data class ServerFormState(
    val serverId: String? = null,
    val type: ServerFormType = ServerFormType.JELLYFIN,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val apiKey: String = "",
    val requestProvider: RequestProviderType = RequestProviderType.SEERR,
    val jellyfinSignInMethod: JellyfinSignInMethod = JellyfinSignInMethod.QUICK_CONNECT,
    val useJellyfinLogin: Boolean = false,
    val automaticSeerrLogin: Boolean = false,
    val requiresSeerrPassword: Boolean = false,
    val allowInsecureHttp: Boolean = false,
) {
    val serverAddressValidation: ServerAddressValidation
        get() = validateServerAddress(baseUrl)

    val requiresInsecureHttpConfirmation: Boolean
        get() =
            serverId == null &&
                baseUrl.trim().startsWith("http://", ignoreCase = true)

    val isValid: Boolean
        get() =
            (!requiresInsecureHttpConfirmation || allowInsecureHttp) &&
                serverAddressValidation is ServerAddressValidation.Valid &&
                when (type) {
                    ServerFormType.JELLYFIN ->
                        name.isNotBlank() &&
                            baseUrl.isNotBlank() &&
                            (
                                jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT ||
                                    (
                                        username.isNotBlank()
                                    )
                            )
                    ServerFormType.SEERR ->
                        baseUrl.isNotBlank() &&
                            if (requestProvider == RequestProviderType.MEDIARIUM) {
                                apiKey.isNotBlank()
                            } else {
                                automaticSeerrLogin ||
                                    (useJellyfinLogin && username.isNotBlank() && (!requiresSeerrPassword || password.isNotBlank())) ||
                                    (!useJellyfinLogin && email.isNotBlank() && password.isNotBlank())
                            }
                }
}
