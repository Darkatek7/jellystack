package app.jellystack.mobile.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import io.github.aakira.napier.Napier

class JellystackCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        Napier.d(tag = "Cast", message = "Using receiver App ID: ${CastConfig.RECEIVER_APP_ID}")
        val notificationOptions =
            NotificationOptions
                .Builder()
                .setTargetActivityClassName("app.jellystack.mobile.MainActivity")
                .build()
        val mediaOptions =
            CastMediaOptions
                .Builder()
                .setNotificationOptions(notificationOptions)
                .build()
        return CastOptions
            .Builder()
            .setReceiverApplicationId(CastConfig.RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
