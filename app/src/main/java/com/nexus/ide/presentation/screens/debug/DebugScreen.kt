package com.nexus.ide.presentation.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.presentation.components.NexusTopBar
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

/**
 * Debug screen — placeholder until a DebugAdapter is wired per-session.
 *
 * The debug adapter is intentionally not passed via Koin. When the
 * debugger feature is complete, a DebugSession will be started from
 * ProjectViewModel and exposed as a StateFlow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(vm: ProjectViewModel) {
    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Debug",
                subtitle = "No active session"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SectionTitle("Call stack")
            EmptyHint("Start a debug session from the editor to see frames here.")

            Spacer(Modifier.height(16.dp))
            SectionTitle("Variables")
            EmptyHint("Variables will appear when the debugger is paused.")

            Spacer(Modifier.height(16.dp))
            SectionTitle("Breakpoints")
            EmptyHint("Set breakpoints by tapping the gutter in the editor.")

            Spacer(Modifier.height(16.dp))
            SectionTitle("Output")
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "No output yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}
