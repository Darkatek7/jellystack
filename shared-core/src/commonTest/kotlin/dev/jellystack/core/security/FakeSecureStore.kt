package dev.jellystack.core.security

class FakeSecureStore : SecureStore {
    private val items = mutableMapOf<String, SecretValue>()

    override suspend fun write(
        key: String,
        value: SecretValue,
    ) {
        items[key] = value
    }

    override suspend fun read(key: String): SecretValue? = items[key]

    override suspend fun remove(key: String) {
        items.remove(key)
    }

    fun peek(key: String): SecretValue? = items[key]
}
