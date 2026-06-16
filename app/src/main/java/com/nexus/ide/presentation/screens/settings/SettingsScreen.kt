package com.nexus.ide.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.data.local.prefs.SettingsStore
import com.nexus.ide.presentation.viewmodels.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ProjectViewModel) {
    val store: SettingsStore = remember { ServiceLocator.settings }

    // Read individual values as State so the UI reacts to changes
    var fontSizeSp by remember { mutableStateOf(store.fontSizeSp) }
    var tabSize by remember { mutableStateOf(store.tabSize.toFloat()) }
    var wordWrap by remember { mutableStateOf(store.wordWrap) }
    var useLigatures by remember { mutableStateOf(store.useLigatures) }
    var showMinimap by remember { mutableStateOf(store.showMinimap) }
    var aiModel by remember { mutableStateOf(store.aiModel) }
    var aiProvider by remember { mutableStateOf(store.aiProvider) }
    var termuxAutoDetect by remember { mutableStateOf(store.termuxAutoDetect) }
    var biometricLock by remember { mutableStateOf(store.biometricLock) }
    var telemetry by remember { mutableStateOf(store.telemetryEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader("Editor") }
            item {
                SettingSlider(
                    title = "Font size",
                    value = fontSizeSp,
                    range = 9f..28f,
                    onChange = {
                        fontSizeSp = it
                        store.fontSizeSp = it
                    }
                )
            }
            item {
                SettingSlider(
                    title = "Tab width",
                    value = tabSize,
                    range = 2f..8f,
                    steps = 6,
                    onChange = {
                        tabSize = it
                        store.tabSize = it.toInt()
                    }
                )
            }
            item {
                SwitchSetting(
                    title = "Word wrap",
                    description = "Wrap long lines at viewport edge",
                    checked = wordWrap,
                    onChange = {
                        wordWrap = it
                        store.wordWrap = it
                    }
                )
            }
            item {
                SwitchSetting(
                    title = "Minimap",
                    description = "Show a minimap overview in the editor gutter",
                    checked = showMinimap,
                    onChange = {
                        showMinimap = it
                        store.showMinimap = it
                    }
                )
            }
            item {
                SwitchSetting(
                    title = "Font ligatures",
                    description = "Use programming ligatures where supported",
                    checked = useLigatures,
                    onChange = {
                        useLigatures = it
                        store.useLigatures = it
                    }
                )
            }
            item { SectionHeader("Terminal") }
            item {
                SwitchSetting(
                    title = "Auto-detect Termux",
                    description = "Automatically connect to an installed Termux session",
                    checked = termuxAutoDetect,
                    onChange = {
                        termuxAutoDetect = it
                        store.termuxAutoDetect = it
                    }
                )
            }
            item { SectionHeader("AI") }
            item {
                OutlinedTextField(
                    value = aiProvider,
                    onValueChange = {
                        aiProvider = it
                        store.aiProvider = it
                    },
                    label = { Text("Provider (openai / openrouter / ollama)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = {
                        aiModel = it
                        store.aiModel = it
                    },
                    label = { Text("Model name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SectionHeader("Security") }
            item {
                SwitchSetting(
                    title = "Biometric lock",
                    description = "Require fingerprint / face unlock to open the app",
                    checked = biometricLock,
                    onChange = {
                        biometricLock = it
                        store.biometricLock = it
                    }
                )
            }
            item { SectionHeader("Privacy") }
            item {
                SwitchSetting(
                    title = "Anonymous telemetry",
                    description = "Help improve NexusIDE by sharing crash reports",
                    checked = telemetry,
                    onChange = {
                        telemetry = it
                        store.telemetryEnabled = it
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description, fontSize = 12.sp) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        }
    )
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text("${value.toInt()}", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}
