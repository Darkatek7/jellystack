package dev.jellystack.core.security.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.jellystack.core.security.SecureStoreEngine

class AndroidSecureStoreEngine(
    context: Context,
    name: String,
) : SecureStoreEngine {
    private val preferences =
        EncryptedSharedPreferences.create(
            context,
            name,
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override fun write(
        key: String,
        value: String,
    ) {
        val success =
            preferences
                .edit()
                .putString(key, value)
                .commit()
        if (!success) {
            throw IllegalStateException("Failed to persist secure entry for key '$key'.")
        }
    }

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun delete(key: String) {
        val success =
            preferences
                .edit()
                .remove(key)
                .commit()
        if (!success) {
            throw IllegalStateException("Failed to remove secure entry for key '$key'.")
        }
    }
}
