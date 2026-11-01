package app.jellystack.tv.dream

import android.service.dreams.DreamService
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.jellystack.tv.R
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.design.tv.TvScreensaver
import dev.jellystack.design.tv.TvScreensaverSlide
import dev.jellystack.design.tv.TvScreensaverSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The system screensaver ("Screen saver" in the TV's settings): backdrops from the active profile's cached library.
 * Nothing runs unless the user picks it there. It reads the local cache only, never refreshes or signs in, and shows
 * a plain dark screen with the app name when there is no usable profile or nothing cached.
 *
 * The process may have been dead: the system starts it for the service, `JellystackTvApplication.onCreate` starts
 * Koin before any component is created, and the stores and the active profile are read from disk.
 */
class JellystackDreamService : DreamService() {
    private var owner: DreamLifecycleOwner? = null
    private var scope: CoroutineScope? = null
    private var composeView: ComposeView? = null
    private var slides by mutableStateOf<List<TvScreensaverSlide>?>(null)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val lifecycleOwner = DreamLifecycleOwner()
        window.decorView.setViewTreeLifecycleOwner(lifecycleOwner)
        window.decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        owner = lifecycleOwner
        val appName = getString(R.string.app_name)
        val view =
            ComposeView(this).apply {
                setContent { TvScreensaver(slides = slides, appName = appName) }
            }
        composeView = view
        setContentView(view, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        lifecycleOwner.onWindowAttached()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        owner?.onDreamStarted()
        scope?.cancel()
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = loadScope
        loadScope.launch { slides = loadSlides() }
    }

    override fun onDreamingStopped() {
        stopWork()
        owner?.onDreamStopped()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        stopWork()
        owner?.onWindowDetached()
        owner = null
        composeView = null
        super.onDetachedFromWindow()
    }

    private fun stopWork() {
        scope?.cancel()
        scope = null
        // Ends the slide loop and its image requests now rather than when the window is finally released.
        composeView?.disposeComposition()
    }

    private suspend fun loadSlides(): List<TvScreensaverSlide> {
        if (!JellystackDI.isStarted()) return emptyList()
        return try {
            JellystackDI.koin.get<TvScreensaverSource>().load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            JellystackLog.w("Screensaver could not start")
            emptyList()
        }
    }
}
