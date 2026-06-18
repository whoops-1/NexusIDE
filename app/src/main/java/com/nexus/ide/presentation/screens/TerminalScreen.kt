package com.nexus.ide.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.terminal.TerminalHost
import com.nexus.ide.presentation.components.NexusTopBar
import com.nexus.ide.presentation.components.TerminalView
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/**
 * Top-level terminal screen.
 *
 * Tab management (spawning, closing, switching shells) is owned
 * entirely by [TerminalView] via [TerminalHost] - this screen is just
 * the chrome around it. [TerminalHost] handles Termux environment
 * injection internally when Termux is installed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: ProjectViewModel) {
    val host = remember {
        TerminalHost(termux = ServiceLocator.termux, onSessionClosed = {})
    }

    Scaffold(
        topBar = { NexusTopBar(title = "Terminal") }
    ) { padding ->
        TerminalView(host = host, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
