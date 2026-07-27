package app.jellystack.mobile.cast

import android.app.Application
import com.google.android.gms.cast.framework.CastContext

object CastInitializer {
    fun initialize(application: Application) {
        CastContext.getSharedInstance(application)
    }
}
