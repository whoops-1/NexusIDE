package com.nexus.ide

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexus.ide.core.di.LocalServiceLocator
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.presentation.navigation.NexusRoot
import com.nexus.ide.presentation.theme.NexusTheme

/** Provides a () -> Unit callback that opens the SAF folder picker. */
val LocalOpenFolderPicker = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Delivers the user-selected folder URI to interested screens.
 * Null until the user picks a folder for the first time this session.
 */
val LocalSelectedFolderUri = staticCompositionLocalOf<Uri?> { null }

/**
 * Single Activity host. All screens are Compose destinations.
 *
 * Hosts the SAF [ActivityResultLauncher] for folder picking here so
 * any screen can call [LocalOpenFolderPicker.current] without needing
 * its own Activity reference.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        ServiceLocator.init(applicationContext)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Folder picker launcher — hoisted here so it survives recomposition
            var selectedFolderUri: Uri? = null
            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    // Persist read permission across restarts
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    selectedFolderUri = uri
                }
            }

            CompositionLocalProvider(
                LocalServiceLocator provides ServiceLocator,
                LocalOpenFolderPicker provides { folderPickerLauncher.launch(null) },
                LocalSelectedFolderUri provides selectedFolderUri
            ) {
                val themeId by ServiceLocator.settings.themeId.collectAsState()
                NexusTheme(themeId = themeId) {
                    NexusRoot(onReady = { ready = true })
                }
            }
        }
    }
}
