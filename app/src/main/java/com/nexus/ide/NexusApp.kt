package com.nexus.ide

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.core.utils.CrashHandler
import com.nexus.ide.core.utils.Logger

/**
 * Application entry point.
 *
 * Wires up the service locator (lightweight DI), installs the crash handler,
 * and tunes a few runtime knobs that have to be set before any Activity is
 * created.
 *
 * Performance notes:
 *  - No reflective Hilt graph. ServiceLocator is a plain object graph that
 *    is created lazily and only for the features the user actually opens.
 *  - StrictMode is enabled in debug builds only.
 */
class NexusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        CrashHandler.install()
        if (BuildConfig.DEBUG) installStrictMode()

        ServiceLocator.init(this)
        Logger.i("NexusApp", "boot complete (sdk=${Build.VERSION.SDK_INT})")
    }

    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    companion object {
        @Volatile
        lateinit var instance: NexusApp
            private set
    }
}
