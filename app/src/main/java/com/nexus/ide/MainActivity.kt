package com.nexus.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexus.ide.core.di.LocalServiceLocator
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.presentation.navigation.NexusRoot
import com.nexus.ide.presentation.theme.NexusTheme

/**
 * Single Activity host. All screens are Compose destinations.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Keep splash until the cold-start work is done.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(LocalServiceLocator provides ServiceLocator) {
                NexusTheme {
                    NexusRoot(onReady = { ready = true })
                }
            }
        }
    }
}
