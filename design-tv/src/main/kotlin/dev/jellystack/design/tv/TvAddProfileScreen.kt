@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.ProfileConnectionBinding
import dev.jellystack.core.profile.ProfileSeerrAutoLinkCoordinator
import dev.jellystack.core.profile.newProfileJellyfinPrefill
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Signs in another household member with the household's Jellyfin address already filled in.
 * An account that already has a profile opens that profile; a new account gets a profile that is signed in to the
 * household's Seerr.
 */
@Composable
internal fun TvAddProfileScreen(
    profiles: List<HouseholdProfile>,
    appVersion: String,
    strings: TvStrings,
    onCreated: (HouseholdProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val koin = remember { JellystackDI.koin }
    val scope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
    val serverRepository = remember(koin) { koin.get<ServerRepository>() }
    val autoLinkCoordinator = remember(koin) { koin.get<ProfileSeerrAutoLinkCoordinator>() }
    var settingUp by remember { mutableStateOf(false) }
    val bindingsFlow = remember(profileRepository) { profileRepository.observeBindings() }
    val bindings: List<ProfileConnectionBinding>? by bindingsFlow.collectAsStateWithLifecycle(initialValue = null)
    // Resolved once when the bindings arrive, so later server changes never reset what the user typed.
    val prefill =
        remember(bindings != null) {
            bindings?.let { newProfileJellyfinPrefill(profiles, it, serverRepository.currentServers()) }
        }
    if (settingUp) {
        TvProfileStatusScreen(strings.settingUpProfile)
        return
    }
    TvConnectionScreen(
        coordinator = remember(koin) { koin.get<ServerConnectionCoordinator>() },
        quickConnectCoordinator = remember(koin) { koin.get<JellyfinQuickConnectCoordinator>() },
        appVersion = appVersion,
        strings = strings,
        onConnected = { connected ->
            settingUp = true
            scope.launch {
                val profile =
                    try {
                        autoLinkCoordinator.openOrCreateProfile(connected, appVersion)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        settingUp = false
                        return@launch
                    }
                onCreated(profile)
            }
        },
        onDismiss = onDismiss,
        refreshExistingAccount = true,
        initialDisplayName = prefill?.name ?: "Jellyfin",
        initialBaseUrl = prefill?.baseUrl.orEmpty(),
    )
}
