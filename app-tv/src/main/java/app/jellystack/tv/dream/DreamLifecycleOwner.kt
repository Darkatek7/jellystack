package app.jellystack.tv.dream

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The lifecycle and saved-state owner Compose looks for on the window's decor view. A `DreamService` is neither
 * a `ComponentActivity` nor a `LifecycleService`, so without this a `ComposeView` fails when it attaches.
 * Main thread only.
 */
internal class DreamLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.INITIALIZED
    }

    /** The window exists: Compose may create its composition, but nothing runs yet. */
    fun onWindowAttached() {
        registry.currentState = Lifecycle.State.CREATED
    }

    /** The dream is on screen. */
    fun onDreamStarted() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** The dream is over; the window may still be attached for a moment. */
    fun onDreamStopped() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.CREATED
    }

    /** The window is gone for good. */
    fun onWindowDetached() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
