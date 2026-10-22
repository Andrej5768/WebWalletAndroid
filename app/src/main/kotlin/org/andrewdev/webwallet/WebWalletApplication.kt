package org.andrewdev.webwallet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WebWalletApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Release builds plant a Crashlytics tree instead, once telemetry consent exists (docs/02 §9)
            Timber.plant(Timber.DebugTree())
        }
    }
}
