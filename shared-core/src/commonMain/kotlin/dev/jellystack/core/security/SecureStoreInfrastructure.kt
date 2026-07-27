package dev.jellystack.core.security

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher

interface SecureStoreEngine {
    @Throws(Exception::class)
    fun write(
        key: String,
        value: String,
    )

    @Throws(Exception::class)
    fun read(key: String): String?

    @Throws(Exception::class)
    fun delete(key: String)
}

interface SecureStoreEngineFactory {
    fun create(name: String): SecureStoreEngine
}

interface SecureStoreFactory {
    fun create(name: String = DEFAULT_SECURE_STORE_NAME): SecureStore

    companion object {
        const val DEFAULT_SECURE_STORE_NAME = "jellystack-secure-store"
    }
}

class SecureStoreFactoryImpl(
    private val engineFactory: SecureStoreEngineFactory,
    private val logger: SecureStoreLogger,
    private val dispatcher: CoroutineDispatcher,
) : SecureStoreFactory {
    override fun create(name: String): SecureStore =
        SecureStoreImpl(
            engine = engineFactory.create(name),
            dispatcher = dispatcher,
            logger = logger,
        )
}

interface SecureStoreLogger {
    fun onSuccess(
        action: SecureStoreAction,
        key: String,
        redactedValue: String? = null,
    )

    fun onFailure(
        action: SecureStoreAction,
        key: String,
        throwable: Throwable,
    )
}

class SecureStoreNapierLogger(
    private val tag: String = "SecureStore",
) : SecureStoreLogger {
    override fun onSuccess(
        action: SecureStoreAction,
        key: String,
        redactedValue: String?,
    ) {
        Napier.d(tag = tag) {
            buildString {
                append("action=${action.name.lowercase()} key=$key result=success")
                if (redactedValue != null) {
                    append(" value=").append(redactedValue)
                }
            }
        }
    }

    override fun onFailure(
        action: SecureStoreAction,
        key: String,
        throwable: Throwable,
    ) {
        Napier.e(tag = tag, throwable = throwable) {
            "action=${action.name.lowercase()} key=$key result=failure"
        }
    }
}
