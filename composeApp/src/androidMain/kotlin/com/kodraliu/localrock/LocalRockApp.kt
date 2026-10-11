package com.kodraliu.localrock

import android.app.Application
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.protocol.initVacLocalAndroidContext
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Owns the [AppContainer] so it belongs to the process rather than to the activity: the
 * background-alerts service has to reach the same connection and monitor after the activity is gone.
 */
class LocalRockApp : Application() {

    val container: AppContainer by lazy {
        AppContainer(SharedPreferencesSettings(getSharedPreferences("vaclocal", MODE_PRIVATE)))
    }

    override fun onCreate() {
        super.onCreate()
        initVacLocalAndroidContext(this)
    }
}
