package app.jellystack.tv

import android.app.Application
import app.jellystack.tv.di.tvAppModule
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.logging.JellystackLog
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JellystackTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Napier.base(DebugAntilog())
        JellystackLog.configure(BuildConfig.DEBUG)
        if (!JellystackDI.isStarted()) {
            startKoin {
                androidContext(this@JellystackTvApplication)
                modules(JellystackDI.modules + tvAppModule)
            }
        }
    }
}
