package dev.jellystack.core.security.ios

import dev.jellystack.core.security.SecureStoreEngine
import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.length
import platform.Foundation.setObject
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kCFBooleanTrue
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus
import platform.posix.memcpy

class IosSecureStoreEngine(
    private val service: String,
) : SecureStoreEngine {
    override fun write(
        key: String,
        value: String,
    ) {
        val data = value.encodeToNSData()
        memScoped {
            delete(key)
            val query =
                baseQuery(key).apply {
                    setObject(data, forKey = kSecValueData)
                    setObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly, forKey = kSecAttrAccessible)
                }
            val status = SecItemAdd(query.toCFDictionary(), null)
            ensureSuccess(status, "write", key)
        }
    }

    override fun read(key: String): String? =
        memScoped {
            val query =
                baseQuery(key).apply {
                    setObject(kCFBooleanTrue, forKey = kSecReturnData)
                    setObject(kSecMatchLimitOne, forKey = kSecMatchLimit)
                }
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query.toCFDictionary(), result.ptr)) {
                errSecSuccess -> {
                    val data = result.value?.reinterpret<NSData>() ?: return@memScoped null
                    data.toByteArray().decodeToString()
                }
                errSecItemNotFound -> null
                else -> throw IllegalStateException("Failed to read key '$key' (status=$status)")
            }
        }

    override fun delete(key: String) {
        memScoped {
            val status = SecItemDelete(baseQuery(key).toCFDictionary())
            if (status != errSecSuccess && status != errSecItemNotFound) {
                ensureSuccess(status, "delete", key)
            }
        }
    }

    private fun baseQuery(key: String): NSMutableDictionary =
        NSMutableDictionary().apply {
            setObject(kSecClassGenericPassword, forKey = kSecClass)
            setObject(service, forKey = kSecAttrService)
            setObject(key, forKey = kSecAttrAccount)
        }

    private fun ensureSuccess(
        status: OSStatus,
        operation: String,
        key: String,
    ) {
        if (status != errSecSuccess) {
            throw IllegalStateException("Failed to $operation key '$key' (status=$status)")
        }
    }
}

private fun NSMutableDictionary.toCFDictionary(): CFDictionaryRef = this

private fun String.encodeToNSData(): NSData =
    NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)
        ?: error("Unable to encode secret for secure storage.")

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, length.convert())
    }
    return bytes
}
