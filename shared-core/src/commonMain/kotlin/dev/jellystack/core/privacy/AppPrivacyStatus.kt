package dev.jellystack.core.privacy

enum class RuntimePermissionStatus {
    Granted,
    NotGranted,
    NotApplicable,
}

data class AppPrivacyStatus(
    val nearbyDevices: RuntimePermissionStatus = RuntimePermissionStatus.NotApplicable,
    val legacyLocation: RuntimePermissionStatus = RuntimePermissionStatus.NotApplicable,
    val notifications: RuntimePermissionStatus = RuntimePermissionStatus.NotApplicable,
)
