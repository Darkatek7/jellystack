package app.jellystack.tv.watchnext

import android.content.SharedPreferences
import java.util.UUID

/** Stores [WatchNextStateStore] in app-private preferences; holds hashes and item ids only. */
internal class SharedPreferencesWatchNextState(
    private val preferences: SharedPreferences,
) : WatchNextStateStore {
    override var owner: String?
        get() = preferences.getString(KEY_OWNER, null)
        set(value) = preferences.edit().putString(KEY_OWNER, value).apply()

    override var known: Set<String>
        get() = preferences.getStringSet(KEY_KNOWN, null).orEmpty().toSet()
        set(value) = preferences.edit().putStringSet(KEY_KNOWN, value.toSet()).apply()

    override var dismissed: Set<String>
        get() = preferences.getStringSet(KEY_DISMISSED, null).orEmpty().toSet()
        set(value) = preferences.edit().putStringSet(KEY_DISMISSED, value.toSet()).apply()

    override val artSalt: String
        get() =
            preferences.getString(KEY_SALT, null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_SALT, it).apply()
            }

    override fun reset() {
        preferences
            .edit()
            .remove(KEY_OWNER)
            .remove(KEY_KNOWN)
            .remove(KEY_DISMISSED)
            .apply()
    }

    private companion object {
        const val KEY_OWNER = "owner"
        const val KEY_KNOWN = "known"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_SALT = "art_salt"
    }
}
