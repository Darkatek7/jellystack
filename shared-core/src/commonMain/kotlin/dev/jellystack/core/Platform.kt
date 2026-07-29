package dev.jellystack.core

import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock

expect class Platform() {
    val name: String
}

fun currentPlatform(): Platform =
    Platform().also {
        Napier.d(message = "Bootstrapped core on ${it.name} at ${Clock.System.now()}")
    }
