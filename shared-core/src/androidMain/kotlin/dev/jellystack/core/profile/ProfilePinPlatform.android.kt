package dev.jellystack.core.profile

import java.security.SecureRandom

internal actual fun secureProfilePinSalt(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)
