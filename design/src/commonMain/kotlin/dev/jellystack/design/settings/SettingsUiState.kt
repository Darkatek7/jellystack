package dev.jellystack.design.settings

import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppPlatformCapabilities
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.AutoplayNextMode
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.core.preferences.StreamingQualityPreference
import dev.jellystack.core.preferences.SubtitleBackground
import dev.jellystack.core.preferences.SubtitleMode
import dev.jellystack.core.preferences.SubtitleTextSize
import dev.jellystack.core.preferences.ThemeMode
import dev.jellystack.core.privacy.AppPrivacyStatus
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricLockState
import dev.jellystack.core.server.ServerType

internal enum class SettingsSection {
    Playback,
    AudioSubtitles,
    AppearanceLanguage,
    Downloads,
    Security,
    Connections,
    About,
}

internal enum class SettingsConnectionHealth {
    Ready,
    NeedsAttention,
}

internal data class SettingsConnectionUi(
    val id: String,
    val type: ServerType,
    val name: String,
    val isActive: Boolean,
    val health: SettingsConnectionHealth,
)

internal data class SettingsUiState(
    val selectedSection: SettingsSection? = null,
    val themeMode: ThemeMode,
    val appSettings: AppSettings = AppSettings(),
    val platformCapabilities: AppPlatformCapabilities = AppPlatformCapabilities(),
    val appLockEnabled: Boolean,
    val appLockState: BiometricLockState,
    val appLockCapability: BiometricCapability,
    val connections: List<SettingsConnectionUi>,
    val appVersion: String,
    val downloadCount: Int = 0,
    val downloadedBytes: Long = 0L,
    val privacyStatus: AppPrivacyStatus = AppPrivacyStatus(),
)

internal sealed interface SettingsAction {
    data class SelectSection(
        val section: SettingsSection?,
    ) : SettingsAction

    data class SetTheme(
        val mode: ThemeMode,
    ) : SettingsAction

    data class SetAppLanguage(
        val language: AppLanguage,
    ) : SettingsAction

    data class SetWifiQuality(
        val quality: StreamingQualityPreference,
    ) : SettingsAction

    data class SetMobileQuality(
        val quality: StreamingQualityPreference,
    ) : SettingsAction

    data class SetAutoplayNextMode(
        val mode: AutoplayNextMode,
    ) : SettingsAction

    data class SetIntroSkipMode(
        val mode: SegmentSkipMode,
    ) : SettingsAction

    data class SetRecapSkipMode(
        val mode: SegmentSkipMode,
    ) : SettingsAction

    data class SetOutroSkipMode(
        val mode: SegmentSkipMode,
    ) : SettingsAction

    data class SetPreviewSkipMode(
        val mode: SegmentSkipMode,
    ) : SettingsAction

    data class SetCommercialSkipMode(
        val mode: SegmentSkipMode,
    ) : SettingsAction

    data class SetResumeMode(
        val mode: ResumeMode,
    ) : SettingsAction

    data class SetSeekBackSeconds(
        val seconds: Int,
    ) : SettingsAction

    data class SetSeekForwardSeconds(
        val seconds: Int,
    ) : SettingsAction

    data class SetPreferredAudioLanguage(
        val languageCode: String?,
    ) : SettingsAction

    data class SetPreferredSubtitleLanguage(
        val languageCode: String?,
    ) : SettingsAction

    data class SetSubtitleMode(
        val mode: SubtitleMode,
    ) : SettingsAction

    data class SetRememberSeriesTracks(
        val enabled: Boolean,
    ) : SettingsAction

    data object ClearRememberedTracks : SettingsAction

    data class SetSubtitleTextSize(
        val size: SubtitleTextSize,
    ) : SettingsAction

    data class SetSubtitleBackground(
        val background: SubtitleBackground,
    ) : SettingsAction

    data class SetSpotlightAutoCycle(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetUseServerHomeSections(
        val enabled: Boolean,
    ) : SettingsAction

    data class SetSpotlightIntervalSeconds(
        val seconds: Int,
    ) : SettingsAction

    data class SetDownloadsWifiOnly(
        val enabled: Boolean,
    ) : SettingsAction

    data object ClearAllDownloads : SettingsAction

    data class SetAppLock(
        val enabled: Boolean,
    ) : SettingsAction

    data class ActivateConnection(
        val id: String,
    ) : SettingsAction

    data class EditConnection(
        val id: String,
    ) : SettingsAction

    data class RemoveConnection(
        val id: String,
    ) : SettingsAction

    data class AddConnection(
        val type: ServerType,
    ) : SettingsAction

    data object RunSetup : SettingsAction

    data object ShowWhatsNew : SettingsAction

    data object Close : SettingsAction
}

internal fun persistPlaybackSegmentSetting(
    action: SettingsAction,
    repository: AppSettingsRepository,
): Boolean =
    when (action) {
        is SettingsAction.SetIntroSkipMode -> repository.setIntroSkipMode(action.mode).let { true }
        is SettingsAction.SetRecapSkipMode -> repository.setRecapSkipMode(action.mode).let { true }
        is SettingsAction.SetOutroSkipMode -> repository.setOutroSkipMode(action.mode).let { true }
        is SettingsAction.SetPreviewSkipMode -> repository.setPreviewSkipMode(action.mode).let { true }
        is SettingsAction.SetCommercialSkipMode -> repository.setCommercialSkipMode(action.mode).let { true }
        else -> false
    }
