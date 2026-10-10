package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.ProfileConnectionBinding
import dev.jellystack.core.profile.newProfileJellyfinPrefill
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.launch

/** Signs in another household member with the household's Jellyfin address already filled in. */
@Composable
internal fun TvAddProfileScreen(
    profiles: List<HouseholdProfile>,
    connectionsBeforeAdd: Set<String>,
    appVersion: String,
    strings: TvStrings,
    onCreated: (HouseholdProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val koin = remember { JellystackDI.koin }
    val scope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
    val serverRepository = remember(koin) { koin.get<ServerRepository>() }
    val bindings: List<ProfileConnectionBinding>? by
        remember(profileRepository) { profileRepository.observeBindings() }.collectAsStateWithLifecycle(initialValue = null)
    // Resolved once when the bindings arrive, so later server changes never reset what the user typed.
    val prefill =
        remember(bindings != null) {
            bindings?.let { newProfileJellyfinPrefill(profiles, it, serverRepository.currentServers()) }
        }
    TvConnectionScreen(
        coordinator = remember(koin) { koin.get<ServerConnectionCoordinator>() },
        quickConnectCoordinator = remember(koin) { koin.get<JellyfinQuickConnectCoordinator>() },
        appVersion = appVersion,
        strings = strings,
        onConnected = {
            scope.launch {
                val connected =
                    serverRepository
                        .currentServers()
                        .filter { it.type == ServerType.JELLYFIN && it.id !in connectionsBeforeAdd }
                        .maxByOrNull { it.updatedAt }
                if (connected == null) {
                    // The account was already stored, so there is no new member to create.
                    onDismiss()
                    return@launch
                }
                val username = (connected.credentials as? StoredCredential.Jellyfin)?.username
                onCreated(
                    profileRepository.createProfile(
                        displayName = username?.takeIf(String::isNotBlank) ?: connected.name,
                        jellyfinConnectionId = connected.id,
                    ),
                )
            }
        },
        onDismiss = onDismiss,
        initialDisplayName = prefill?.name ?: "Jellyfin",
        initialBaseUrl = prefill?.baseUrl.orEmpty(),
    )
}
