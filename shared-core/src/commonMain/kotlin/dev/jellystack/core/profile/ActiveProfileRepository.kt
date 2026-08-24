package dev.jellystack.core.profile

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveProfileRepository(
    private val settings: Settings,
) {
    private val mutableProfileId = MutableStateFlow(read())
    val profileId: StateFlow<String?> = mutableProfileId.asStateFlow()

    fun activate(profileId: String) {
        require(profileId.isNotBlank())
        settings.putString(KEY_ACTIVE_PROFILE, profileId)
        mutableProfileId.value = profileId
    }

    fun clear() {
        settings.remove(KEY_ACTIVE_PROFILE)
        mutableProfileId.value = null
    }

    private fun read(): String? = settings.getStringOrNull(KEY_ACTIVE_PROFILE)?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val KEY_ACTIVE_PROFILE = "profiles.active"
    }
}
