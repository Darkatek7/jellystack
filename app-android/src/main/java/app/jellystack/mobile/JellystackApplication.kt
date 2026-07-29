package app.jellystack.mobile

import android.app.Application
import app.jellystack.mobile.cast.CastInitializer
import app.jellystack.mobile.di.androidAppModule
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.logging.JellystackLog
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.net.UnknownHostException

class JellystackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
        }
        JellystackLog.configure(BuildConfig.DEBUG)
        CastInitializer.initialize(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable.isOfflineDnsFailure()) {
                Napier.w(
                    message = "Suppressed offline network failure on ${thread.name}",
                    throwable = throwable,
                )
            } else {
                Napier.e(
                    message = "Uncaught exception on ${thread.name}",
                    throwable = throwable,
                )
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
        if (!JellystackDI.isStarted()) {
            startKoin {
                androidContext(this@JellystackApplication)
                modules(JellystackDI.modules + androidAppModule)
            }
        }
    }
}

private fun Throwable.isOfflineDnsFailure(): Boolean =
    generateSequence(this as Throwable?) { current -> current.cause }
        .any { cause -> cause is UnknownHostException }
