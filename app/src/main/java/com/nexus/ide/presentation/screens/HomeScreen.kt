package com.nexus.ide.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ide.presentation.navigation.LocalTabs
import com.nexus.ide.presentation.navigation.Tab
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ProjectViewModel) {
    val tabs = LocalTabs.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NexusIDE") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Build, run, debug — all from your phone.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { tabs.open(Tab.Editor) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Code, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Editor")
                        }
                        OutlinedButton(onClick = { tabs.open(Tab.Terminal) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Terminal, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Terminal")
                        }
                    }
                }
            }
            Text("Tools", style = MaterialTheme.typography.titleMedium)
            FlowRowGrid(
                items = listOf(
                    Triple(Icons.Default.Terminal, "Terminal", { tabs.open(Tab.Terminal) }),
                    Triple(Icons.Default.AccountTree, "Git", { tabs.open(Tab.Git) }),
                    Triple(Icons.Default.SmartToy, "AI", { tabs.open(Tab.AI) }),
                    Triple(Icons.Default.BugReport, "Debug", { tabs.open(Tab.Debug) }),
                    Triple(Icons.Default.Settings, "Settings", { tabs.open(Tab.Settings) })
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FlowRowGrid(
    items: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { (icon, label, action) ->
                    ElevatedCard(
                        onClick = action,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.4f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                // Fill remaining slots in the last row so cards don't stretch
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
