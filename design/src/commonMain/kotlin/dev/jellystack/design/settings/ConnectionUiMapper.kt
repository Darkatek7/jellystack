package dev.jellystack.design.settings

import dev.jellystack.core.server.ManagedServer

internal fun ManagedServer.toSettingsConnectionUi(
    isActive: Boolean,
    health: SettingsConnectionHealth,
): SettingsConnectionUi =
    SettingsConnectionUi(
        id = id,
        type = type,
        name = name,
        isActive = isActive,
        health = health,
    )
