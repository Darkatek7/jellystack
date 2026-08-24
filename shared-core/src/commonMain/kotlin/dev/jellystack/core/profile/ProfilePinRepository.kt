package dev.jellystack.core.profile

import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ProfilePinRepository(
    private val secureStore: SecureStore,
    private val clock: Clock = Clock.System,
    private val saltGenerator: () -> String = { secureProfilePinSalt().toHex() },
    private val workFactor: Int = DEFAULT_WORK_FACTOR,
) {
    private val mutex = Mutex()

    init {
        require(workFactor > 0)
    }

    suspend fun state(profileId: String): ProfilePinState =
        mutex.withLock {
            stateLocked(profileId)
        }

    suspend fun configure(
        profileId: String,
        pin: String,
    ) {
        requireValidProfileId(profileId)
        requireValidPin(pin)
        mutex.withLock {
            val salt = saltGenerator().also { require(it.isNotBlank()) }
            val digest = deriveDigest(pin, salt, workFactor)
            secureStore.write(verifierKey(profileId), secretValue("$FORMAT_VERSION:$workFactor:$salt:$digest"))
            secureStore.remove(attemptKey(profileId))
        }
    }

    suspend fun verify(
        profileId: String,
        pin: String,
    ): ProfilePinResult {
        requireValidProfileId(profileId)
        requireValidPin(pin)
        return mutex.withLock {
            when (val currentState = stateLocked(profileId)) {
                ProfilePinState.NotConfigured -> ProfilePinResult.Unlocked
                is ProfilePinState.Locked -> ProfilePinResult.Locked(currentState.until)
                ProfilePinState.Ready -> verifyReadyProfile(profileId, pin)
            }
        }
    }

    suspend fun remove(profileId: String) {
        requireValidProfileId(profileId)
        mutex.withLock {
            removeLocked(profileId)
        }
    }

    suspend fun recoverAfterReauthentication(
        profileId: String,
        reauthenticateExactProfile: suspend (profileId: String) -> Boolean,
    ): Boolean {
        requireValidProfileId(profileId)
        if (!reauthenticateExactProfile(profileId)) return false
        mutex.withLock {
            removeLocked(profileId)
        }
        return true
    }

    private suspend fun stateLocked(profileId: String): ProfilePinState {
        requireValidProfileId(profileId)
        if (secureStore.read(verifierKey(profileId)) == null) return ProfilePinState.NotConfigured
        val attempt = readAttempt(profileId)
        val deadline = attempt.lockoutUntilEpochMillis
        if (deadline != null && deadline > clock.now().toEpochMilliseconds()) {
            return ProfilePinState.Locked(Instant.fromEpochMilliseconds(deadline))
        }
        if (deadline != null) secureStore.remove(attemptKey(profileId))
        return ProfilePinState.Ready
    }

    private suspend fun verifyReadyProfile(
        profileId: String,
        pin: String,
    ): ProfilePinResult {
        val verifier =
            secureStore
                .read(verifierKey(profileId))
                ?.reveal()
                ?.let(::parseVerifier)
                ?: error("Profile PIN verifier is invalid")
        val actual = deriveDigest(pin, verifier.salt, verifier.workFactor)
        if (constantTimeEquals(verifier.digest, actual)) {
            secureStore.remove(attemptKey(profileId))
            return ProfilePinResult.Unlocked
        }

        val failures = readAttempt(profileId).failures + 1
        if (failures >= MAX_ATTEMPTS) {
            val until = clock.now().toEpochMilliseconds() + LOCKOUT_MILLIS
            writeAttempt(profileId, AttemptState(MAX_ATTEMPTS, until))
            return ProfilePinResult.Locked(Instant.fromEpochMilliseconds(until))
        }
        writeAttempt(profileId, AttemptState(failures, null))
        return ProfilePinResult.Rejected(MAX_ATTEMPTS - failures)
    }

    private suspend fun removeLocked(profileId: String) {
        secureStore.remove(verifierKey(profileId))
        secureStore.remove(attemptKey(profileId))
    }

    private suspend fun readAttempt(profileId: String): AttemptState {
        val parts = secureStore.read(attemptKey(profileId))?.reveal()?.split(':') ?: return AttemptState.EMPTY
        if (parts.size != 2) return AttemptState.EMPTY
        return AttemptState(
            failures = parts[0].toIntOrNull()?.coerceIn(0, MAX_ATTEMPTS) ?: 0,
            lockoutUntilEpochMillis = parts[1].toLongOrNull()?.takeIf { it > 0 },
        )
    }

    private suspend fun writeAttempt(
        profileId: String,
        attempt: AttemptState,
    ) {
        secureStore.write(
            attemptKey(profileId),
            secretValue("${attempt.failures}:${attempt.lockoutUntilEpochMillis ?: 0}"),
        )
    }

    private fun parseVerifier(serialized: String): PinVerifier? {
        val parts = serialized.split(':')
        if (parts.size != 4 || parts[0] != FORMAT_VERSION) return null
        val parsedWorkFactor = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_WORK_FACTOR } ?: return null
        if (parts[2].isBlank() || parts[3].length != SHA_256_HEX_LENGTH) return null
        return PinVerifier(parsedWorkFactor, parts[2], parts[3])
    }

    private fun deriveDigest(
        pin: String,
        salt: String,
        iterations: Int,
    ): String {
        val saltBytes = salt.encodeToByteArray()
        val pinBytes = pin.encodeToByteArray()
        var digest = Sha256.digest(saltBytes + pinBytes)
        repeat(iterations - 1) {
            digest = Sha256.digest(digest + saltBytes + pinBytes)
        }
        return digest.toHex()
    }

    private fun requireValidProfileId(profileId: String) {
        require(profileId.isNotBlank())
        require(profileId.none { it == ':' || it == '\n' || it == '\r' })
    }

    private fun requireValidPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all { it in '0'..'9' })
    }

    private fun verifierKey(profileId: String) = "profile.pin.$profileId.verifier"

    private fun attemptKey(profileId: String) = "profile.pin.$profileId.attempt"

    private class PinVerifier(
        val workFactor: Int,
        val salt: String,
        val digest: String,
    )

    private data class AttemptState(
        val failures: Int,
        val lockoutUntilEpochMillis: Long?,
    ) {
        companion object {
            val EMPTY = AttemptState(0, null)
        }
    }

    private companion object {
        const val FORMAT_VERSION = "v1"
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L
        const val DEFAULT_WORK_FACTOR = 120_000
        const val MAX_WORK_FACTOR = 1_000_000
        const val SHA_256_HEX_LENGTH = 64
    }
}

internal expect fun secureProfilePinSalt(size: Int = 16): ByteArray

private fun constantTimeEquals(
    expected: String,
    actual: String,
): Boolean {
    var difference = expected.length xor actual.length
    val maximum = maxOf(expected.length, actual.length)
    for (index in 0 until maximum) {
        difference =
            difference or
            (expected.getOrElse(index) { '\u0000' }.code xor actual.getOrElse(index) { '\u0000' }.code)
    }
    return difference == 0
}

private fun ByteArray.toHex(): String =
    buildString(size * 2) {
        this@toHex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

private const val HEX_DIGITS = "0123456789abcdef"

private object Sha256 {
    private val initial =
        intArrayOf(
            0x6a09e667,
            0xbb67ae85.toInt(),
            0x3c6ef372,
            0xa54ff53a.toInt(),
            0x510e527f,
            0x9b05688c.toInt(),
            0x1f83d9ab,
            0x5be0cd19,
        )
    private val constants =
        intArrayOf(
            0x428a2f98,
            0x71374491,
            0xb5c0fbcf.toInt(),
            0xe9b5dba5.toInt(),
            0x3956c25b,
            0x59f111f1,
            0x923f82a4.toInt(),
            0xab1c5ed5.toInt(),
            0xd807aa98.toInt(),
            0x12835b01,
            0x243185be,
            0x550c7dc3,
            0x72be5d74,
            0x80deb1fe.toInt(),
            0x9bdc06a7.toInt(),
            0xc19bf174.toInt(),
            0xe49b69c1.toInt(),
            0xefbe4786.toInt(),
            0x0fc19dc6,
            0x240ca1cc,
            0x2de92c6f,
            0x4a7484aa,
            0x5cb0a9dc,
            0x76f988da,
            0x983e5152.toInt(),
            0xa831c66d.toInt(),
            0xb00327c8.toInt(),
            0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(),
            0xd5a79147.toInt(),
            0x06ca6351,
            0x14292967,
            0x27b70a85,
            0x2e1b2138,
            0x4d2c6dfc,
            0x53380d13,
            0x650a7354,
            0x766a0abb,
            0x81c2c92e.toInt(),
            0x92722c85.toInt(),
            0xa2bfe8a1.toInt(),
            0xa81a664b.toInt(),
            0xc24b8b70.toInt(),
            0xc76c51a3.toInt(),
            0xd192e819.toInt(),
            0xd6990624.toInt(),
            0xf40e3585.toInt(),
            0x106aa070,
            0x19a4c116,
            0x1e376c08,
            0x2748774c,
            0x34b0bcb5,
            0x391c0cb3,
            0x4ed8aa4a,
            0x5b9cca4f,
            0x682e6ff3,
            0x748f82ee,
            0x78a5636f,
            0x84c87814.toInt(),
            0x8cc70208.toInt(),
            0x90befffa.toInt(),
            0xa4506ceb.toInt(),
            0xbef9a3f7.toInt(),
            0xc67178f2.toInt(),
        )

    fun digest(input: ByteArray): ByteArray {
        val padded = pad(input)
        val hash = initial.copyOf()
        val words = IntArray(64)
        for (chunkStart in padded.indices step 64) {
            for (index in 0 until 16) {
                val offset = chunkStart + index * 4
                words[index] =
                    ((padded[offset].toInt() and 0xff) shl 24) or
                    ((padded[offset + 1].toInt() and 0xff) shl 16) or
                    ((padded[offset + 2].toInt() and 0xff) shl 8) or
                    (padded[offset + 3].toInt() and 0xff)
            }
            for (index in 16 until 64) {
                val s0 = words[index - 15].rotateRight(7) xor words[index - 15].rotateRight(18) xor (words[index - 15] ushr 3)
                val s1 = words[index - 2].rotateRight(17) xor words[index - 2].rotateRight(19) xor (words[index - 2] ushr 10)
                words[index] = words[index - 16] + s0 + words[index - 7] + s1
            }
            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]
            for (index in 0 until 64) {
                val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choose = (e and f) xor (e.inv() and g)
                val temporary1 = h + sum1 + choose + constants[index] + words[index]
                val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temporary2 = sum0 + majority
                h = g
                g = f
                f = e
                e = d + temporary1
                d = c
                c = b
                b = a
                a = temporary1 + temporary2
            }
            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h
        }
        return ByteArray(32) { index ->
            (hash[index / 4] ushr (24 - (index % 4) * 8)).toByte()
        }
    }

    private fun pad(input: ByteArray): ByteArray {
        val paddedLength = ((input.size + 9 + 63) / 64) * 64
        val result = ByteArray(paddedLength)
        input.copyInto(result)
        result[input.size] = 0x80.toByte()
        val bitLength = input.size.toLong() * 8L
        for (index in 0 until 8) {
            result[paddedLength - 1 - index] = (bitLength ushr (index * 8)).toByte()
        }
        return result
    }
}
