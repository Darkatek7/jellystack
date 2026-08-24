package dev.jellystack.core.profile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureProfilePinSalt(size: Int): ByteArray =
    ByteArray(size).also { bytes ->
        val status = bytes.usePinned { pinned -> SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0)) }
        check(status == errSecSuccess) { "Unable to generate secure profile PIN salt" }
    }
