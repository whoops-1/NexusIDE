package com.nexus.ide.presentation.screens.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.ide.core.di.ServiceLocator
import com.nexus.ide.features.git.GitRepository
import com.nexus.ide.features.git.GitService
import com.nexus.ide.presentation.components.NexusTopBar
import com.nexus.ide.presentation.screens.diff.DiffViewer
import com.nexus.ide.presentation.viewmodels.ProjectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GitTab { Changes, Diff, Log, Branches }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(vm: ProjectViewModel) {
    val workspaceRoot = remember { ServiceLocator.workspace.workspaceRoot }
    val repo = remember { GitRepository(workspaceRoot) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(GitTab.Changes) }
    var status by remember { mutableStateOf<List<GitRepository.StatusEntry>>(emptyList()) }
    var branch by remember { mutableStateOf<String?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var diffText by remember { mutableStateOf("") }
    var diffFile by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var commitBody by remember { mutableStateOf("") }
    var isRepo by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }
    var busyOp by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            isRepo = repo.service.isRepo()
            status = repo.parseStatus()
            branch = repo.currentBranch()
            val logResult = repo.service.log()
            logLines = if (logResult.ok) logResult.stdout.lines().filter { it.isNotBlank() } else emptyList()
            val branchResult = repo.service.branch()
            branches = if (branchResult.ok) branchResult.stdout.lines().filter { it.isNotBlank() } else emptyList()
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun runGit(op: suspend () -> GitService.CommandResult) {
        scope.launch {
            busyOp = true
            val result = withContext(Dispatchers.IO) { op() }
            feedbackMsg = if (result.ok) result.stdout.ifBlank { "Done" }.take(120)
                          else "Error: ${result.stderr.take(120)}"
            busyOp = false
            refresh()
        }
    }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Source Control",
                subtitle = if (isRepo) (branch ?: "detached") else "Not a git repo",
                actions = {
                    if (busyOp) CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { runGit { repo.service.fetch() } }) { Icon(Icons.Default.CloudDownload, "Fetch") }
                    IconButton(onClick = { runGit { repo.service.pull() } }) { Icon(Icons.Default.CallMerge, "Pull") }
                    IconButton(onClick = { runGit { repo.service.push() } }) { Icon(Icons.Default.CloudUpload, "Push") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Feedback snackbar inline
            feedbackMsg?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { feedbackMsg = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (!isRepo) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Source, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Not a Git repository", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { runGit { repo.service.init() } }) { Text("git init") }
                    }
                }
                return@Scaffold
            }

            // Tab row
            TabRow(selectedTabIndex = tab.ordinal) {
                GitTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.name, fontSize = 12.sp) })
                }
            }

            when (tab) {
                GitTab.Changes -> ChangesTab(
                    status = status,
                    subject = subject,
                    commitBody = commitBody,
                    onSubjectChange = { subject = it },
                    onBodyChange = { commitBody = it },
                    onStageAll = { runGit { repo.service.addAll() } },
                    onStageFile = { path -> runGit { repo.service.add(path) } },
                    onCommit = {
                        val msg = if (commitBody.isBlank()) subject else "$subject\n\n$commitBody"
                        runGit { repo.service.commit(msg) }
                        subject = ""; commitBody = ""
                    },
                    onViewDiff = { path ->
                        scope.launch(Dispatchers.IO) {
                            val r = repo.service.diff()
                            diffText = r.stdout; diffFile = path; tab = GitTab.Diff
                        }
                    }
                )
                GitTab.Diff -> DiffViewer(
                    diff = diffText,
                    filename = diffFile,
                    onAccept = null,
                    onReject = null,
                    modifier = Modifier.fillMaxSize()
                )
                GitTab.Log -> LogTab(logLines)
                GitTab.Branches -> BranchesTab(
                    branches = branches,
                    currentBranch = branch,
                    onCheckout = { b -> runGit { repo.service.checkout(b.trim().removePrefix("* ").trim().substringBefore(" ")) } },
                    onNewBranch = { name -> runGit { repo.service.createBranch(name) } }
                )
            }
        }
    }
}

@Composable
private fun ChangesTab(
    status: List<GitRepository.StatusEntry>,
    subject: String,
    commitBody: String,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onStageAll: () -> Unit,
    onStageFile: (String) -> Unit,
    onCommit: () -> Unit,
    onViewDiff: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onStageAll, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Stage all", fontSize = 12.sp) }
        }
        HorizontalDivider(thickness = 0.5.dp)
        if (status.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(32.dp), tint = Color(0xFF3fb950))
                    Spacer(Modifier.height(8.dp))
                    Text("Working tree clean", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(status) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onViewDiff(entry.path) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val color = when {
                            entry.isUntracked -> Color(0xFF8b949e)
                            entry.isStaged -> Color(0xFF3fb950)
                            else -> Color(0xFFf0883e)
                        }
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${entry.indexStatus}${entry.workTreeStatus}",
                            color = color, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(entry.path, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (!entry.isStaged) {
                            TextButton(onClick = { onStageFile(entry.path) }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(26.dp)) {
                                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.3.dp)
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(value = subject, onValueChange = onSubjectChange, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = commitBody, onValueChange = onBodyChange, label = { Text("Description (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCommit,
                enabled = subject.isNotBlank() && status.any { it.isStaged },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Commit") }
        }
    }
}

@Composable
private fun LogTab(lines: List<String>) {
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No commits yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines) { line ->
                val hash = line.take(7)
                val msg = line.drop(8)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(hash, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(thickness = 0.3.dp)
            }
        }
    }
}

@Composable
private fun BranchesTab(branches: List<String>, currentBranch: String?, onCheckout: (String) -> Unit, onNewBranch: (String) -> Unit) {
    var newBranchName by remember { mutableStateOf("") }
    var showNewBranch by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { showNewBranch = !showNewBranch }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("New branch", fontSize = 12.sp)
            }
        }
        if (showNewBranch) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newBranchName, onValueChange = { newBranchName = it }, label = { Text("Branch name") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { if (newBranchName.isNotBlank()) { onNewBranch(newBranchName); newBranchName = ""; showNewBranch = false } }, enabled = newBranchName.isNotBlank()) { Text("Create") }
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(thickness = 0.5.dp)
        LazyColumn(Modifier.fillMaxSize()) {
            items(branches) { b ->
                val isCurrent = b.trimStart().startsWith("*")
                Row(
                    Modifier.fillMaxWidth().clickable { onCheckout(b) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isCurrent) Icons.Default.Check else Icons.Default.AccountTree, null, modifier = Modifier.size(14.dp), tint = if (isCurrent) Color(0xFF3fb950) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(b.trim(), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal, color = if (isCurrent) Color(0xFF3fb950) else MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(thickness = 0.3.dp)
            }
        }
    }
}
