package com.nexus.ide.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.ide.presentation.screens.ai.AiChatScreen
import com.nexus.ide.presentation.screens.debug.DebugScreen
import com.nexus.ide.presentation.screens.EditorScreen
import com.nexus.ide.presentation.screens.git.GitScreen
import com.nexus.ide.presentation.screens.HomeScreen
import com.nexus.ide.presentation.screens.settings.SettingsScreen
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/** Single enum for the top-level tabs in the main shell. */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Editor("Edit", Icons.Default.Code),
    Terminal("Term", Icons.Default.Terminal),
    Git("Git", Icons.Default.Source),
    AI("AI", Icons.Default.SmartToy),
    Debug("Debug", Icons.Default.BugReport),
    Settings("More", Icons.Default.Settings);

    companion object {
        fun fromRoute(r: String): Tab = values().firstOrNull { it.name.equals(r, true) } ?: Home
    }
}

class TabController {
    var current by mutableStateOf(Tab.Home)
        private set
    fun open(tab: Tab) { current = tab }
    fun back() { current = Tab.Home }
}

@Composable
fun rememberTabController(): TabController = remember { TabController() }

val LocalTabs = staticCompositionLocalOf<TabController> { error("Tabs not provided") }

/** The active workspace (a folder on disk). */
data class Workspace(val root: java.io.File) {
    val name: String get() = root.name
}

val LocalWorkspace = staticCompositionLocalOf<Workspace> { error("Workspace not provided") }

/**
 * The single root composable. Hosts the [Scaffold] with a bottom navigation
 * bar, the current tab's content, and the persistent [ProjectViewModel].
 */
@Composable
fun NexusRoot(onReady: () -> Unit = {}) {
    val tabs = rememberTabController()
    val vm = remember { ProjectViewModel() }

    CompositionLocalProvider(LocalTabs provides tabs) {
        Scaffold(
            bottomBar = { NexusBottomBar(tabs.current) { tabs.open(it) } }
        ) { padding ->
            NexusTabHost(
                tab = tabs.current,
                vm = vm,
                padding = padding,
                onReady = onReady
            )
        }
    }
}

@Composable
private fun NexusBottomBar(current: Tab, onClick: (Tab) -> Unit) {
    NavigationBar {
        Tab.values().forEach { t ->
            NavigationBarItem(
                selected = current == t,
                onClick = { onClick(t) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label) }
            )
        }
    }
}

@Composable
private fun NexusTabHost(
    tab: Tab,
    vm: ProjectViewModel,
    padding: PaddingValues,
    onReady: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onReady() }
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        when (tab) {
            Tab.Home -> HomeScreen(vm)
            Tab.Editor -> EditorScreen(vm)
            Tab.Terminal -> com.nexus.ide.presentation.screens.TerminalScreen(vm)
            Tab.Git -> GitScreen(vm)
            Tab.AI -> AiChatScreen(vm)
            Tab.Debug -> DebugScreen(vm)
            Tab.Settings -> SettingsScreen(vm)
        }
    }
}
