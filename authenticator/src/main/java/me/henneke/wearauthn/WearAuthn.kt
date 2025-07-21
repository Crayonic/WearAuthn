package me.henneke.wearauthn

import android.app.Application
import timber.log.Timber

class WearAuthn: Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Keep the original logging system as well
        Logging.init(applicationContext)
    }
}