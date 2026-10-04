package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

@Immutable
internal data class TvProfilePresentation(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val pinRequired: Boolean,
    val lastActiveAt: Instant?,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }

    val fallbackInitial: String
        get() = displayName.firstOrNull(Char::isLetterOrDigit)?.uppercase() ?: "?"
}

internal fun selectInitialTvProfileId(
    profiles: List<TvProfilePresentation>,
    rememberedProfileId: String?,
): String? = profiles.firstOrNull { it.id == rememberedProfileId }?.id ?: profiles.firstOrNull()?.id
