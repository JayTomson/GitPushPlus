package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GitProject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    project: GitProject,
    viewModel: AppViewModel,
    isSplitView: Boolean = false,
    onNavigateBack: () -> Unit = {}
) {
    val commits by viewModel.projectCommits.collectAsStateWithLifecycle()
    val branches by viewModel.projectBranches.collectAsStateWithLifecycle()
    val isLoadingCommits by viewModel.isLoadingCommits.collectAsStateWithLifecycle()
    val isLoadingBranches by viewModel.isLoadingBranches.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMsg.collectAsStateWithLifecycle()
    val successMsg by viewModel.gitActionSuccessMsg.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    var currentBranch by remember { mutableStateOf(project.defaultBranch) }

    // Workspace Management States
    val localFiles by viewModel.localFiles.collectAsStateWithLifecycle()
    val modifiedFiles by viewModel.modifiedFiles.collectAsStateWithLifecycle()
    val isLoadingWorkspace by viewModel.isLoadingWorkspace.collectAsStateWithLifecycle()

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showEditFileDialog by remember { mutableStateOf(false) }
    var relativePathInput by remember { mutableStateOf("") }
    var fileTextContentInput by remember { mutableStateOf("") }
    var workspaceCommitMsg by remember { mutableStateOf("Push local modifications") }

    var selectedEditingFileRelativePath by remember { mutableStateOf("") }
    var selectedEditingFileContent by remember { mutableStateOf("") }

    // Merge States
    var mergeBaseBranch by remember { mutableStateOf(project.defaultBranch) }
    var mergeHeadBranch by remember { mutableStateOf("") }
    var mergeMessage by remember { mutableStateOf("") }

    var expandedBaseDropdown by remember { mutableStateOf(false) }
    var expandedHeadDropdown by remember { mutableStateOf(false) }

    // New Branch Creation State
    var showCreateBranchDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.updateProjectLocalFolderPath(context, uri.toString())
        }
    }

    val displayFolderName = remember(project.localFolderPath) {
        if (project.localFolderPath.startsWith("content://")) {
            try {
                val parsedUri = android.net.Uri.parse(project.localFolderPath)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parsedUri)
                docFile?.name ?: "System Folder"
            } catch (e: Exception) {
                "System Folder"
            }
        } else {
            project.localFolderPath.ifBlank { "Internal Storage" }
        }
    }

    // Sync on launch or when selection changes
    LaunchedEffect(project.id, currentBranch) {
        viewModel.fetchBranches(project.repoOwner, project.repoName)
        viewModel.fetchCommits(project.repoOwner, project.repoName, currentBranch)
        viewModel.scanLocalWorkspace(context)
    }

    Scaffold(
        topBar = {
            if (!isSplitView) {
                TopAppBar(
                    title = {
                        Column {
                            Text(project.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${project.repoOwner}/${project.repoName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchCommits(project.repoOwner, project.repoName, currentBranch) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSplitView) PaddingValues(0.dp) else innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Split-View Header when embedded
            if (isSplitView) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        Text(
                            text = "${project.repoOwner}/${project.repoName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = {
                        viewModel.fetchBranches(project.repoOwner, project.repoName)
                        viewModel.fetchCommits(project.repoOwner, project.repoName, currentBranch)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Notification / Banner Box (Error & Success)
            AnimatedVisibility(visible = errorMsg != null || successMsg != null) {
                val isSuccess = successMsg != null
                Surface(
                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = successMsg ?: errorMsg ?: "",
                            color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            viewModel.clearError()
                            viewModel.clearSuccessMsg()
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Branch selection widget section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Branch: $currentBranch", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        IconButton(
                            onClick = { showCreateBranchDialog = true },
                            modifier = Modifier.size(24.dp).testTag("create_branch_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Branch", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoadingBranches) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(branches) { branch ->
                                FilterChip(
                                    selected = currentBranch == branch.name,
                                    onClick = { currentBranch = branch.name },
                                    label = { Text(branch.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Tab navigation for Commit histories, Commit file, and Merge operations
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Commits Log") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Local Workspace") },
                    icon = { Icon(Icons.Default.SnippetFolder, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Merge Branch") },
                    icon = { Icon(Icons.Default.Merge, contentDescription = null) }
                )
            }

            // Tabs implementation
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> {
                        // Commits list
                        if (isLoadingCommits) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (commits.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No commits found on branch '$currentBranch'.\nMake a connection or select another branch.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(commits) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = item.commitDetail.author?.name ?: "Unknown",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = item.sha.take(7),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.commitDetail.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.CalendarToday,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = formatDate(item.commitDetail.author?.date ?: ""),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Local Workspace Center Console
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Manage file changes on device, view modified lines, and push updates in batches.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Workspace directory info & Sync control
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    "Git Local Workspace Folder",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = displayFolderName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = { folderPickerLauncher.launch(null) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("select_device_folder_btn")
                                        ) {
                                            Text(if (project.localFolderPath.isBlank()) "Select" else "Change")
                                        }
                                    }
                                    
                                    if (project.localFolderPath.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (project.localFolderPath.startsWith("content://")) {
                                                "Storage URI: ${project.localFolderPath}"
                                            } else {
                                                "Internal Path: ${project.localFolderPath}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.syncWorkspaceFromGitHub(context) },
                                            enabled = !isLoadingWorkspace && project.localFolderPath.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isLoadingWorkspace) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sync/Clone Repo")
                                        }

                                        Button(
                                            onClick = {
                                                relativePathInput = ""
                                                fileTextContentInput = ""
                                                showCreateFileDialog = true
                                            },
                                            enabled = project.localFolderPath.isNotBlank(),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).testTag("new_local_file_btn")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("New File")
                                        }
                                    }
                                }
                            }

                            // localFiles inventory tree
                            Text(
                                "On-Device Files (${localFiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (localFiles.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "No local files stored yet.\nTap 'Sync/Clone Repo' to check out remote source files or create a new one.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    localFiles.forEach { file ->
                                        val relPath = file.relativePath
                                        
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedEditingFileRelativePath = relPath
                                                    selectedEditingFileContent = if (file.uriString != null) {
                                                        try {
                                                            context.contentResolver.openInputStream(android.net.Uri.parse(file.uriString))?.use { stream ->
                                                                stream.bufferedReader(Charsets.UTF_8).readText()
                                                            } ?: ""
                                                        } catch(e: Exception) { "" }
                                                    } else {
                                                        try { file.file?.readText(Charsets.UTF_8) ?: "" } catch(e: Exception) { "" }
                                                    }
                                                    showEditFileDialog = true
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        relPath,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${file.size} B",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    IconButton(
                                                        onClick = { viewModel.deleteWorkspaceFile(context, relPath) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete Local File", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // modifiedFiles status differences
                            Text(
                                "Modified / Untracked Changes (${modifiedFiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (modifiedFiles.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Working tree clean. No differences identified.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    modifiedFiles.forEach { mod ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (mod.isNew) Color(0xFF00796B).copy(alpha = 0.4f) else Color(0xFFD84315).copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                        Icon(
                                                            Icons.Default.FiberNew,
                                                            contentDescription = null,
                                                            tint = if (mod.isNew) Color(0xFF00796B) else Color(0xFFD84315),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            mod.relativePath,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontFamily = FontFamily.Monospace,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Surface(
                                                        color = if (mod.isNew) Color(0xFFE0F2F1) else Color(0xFFFBE9E7),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(
                                                            text = if (mod.isNew) "UNTRACKED / NEW" else "MODIFIED",
                                                            color = if (mod.isNew) Color(0xFF00796B) else Color(0xFFD84315),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Push console
                                    OutlinedTextField(
                                        value = workspaceCommitMsg,
                                        onValueChange = { workspaceCommitMsg = it },
                                        label = { Text("Commit Message") },
                                        modifier = Modifier.fillMaxWidth(),
                                        leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.pushModifiedFiles(context, modifiedFiles, workspaceCommitMsg)
                                        },
                                        enabled = workspaceCommitMsg.isNotBlank() && !isLoadingWorkspace,
                                        modifier = Modifier.fillMaxWidth().testTag("push_workspace_btn"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Push and Commit to Remote Branch")
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Merge Cockpit panel
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Merge Git Branches cleanly. Choose a head branch that will be merged into your target base branch.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Base Branch select box
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Base Target Branch", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box {
                                        OutlinedCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedBaseDropdown = true },
                                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    mergeBaseBranch,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = expandedBaseDropdown,
                                            onDismissRequest = { expandedBaseDropdown = false }
                                        ) {
                                            branches.forEach { br ->
                                                DropdownMenuItem(
                                                    text = { Text(br.name, fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        mergeBaseBranch = br.name
                                                        expandedBaseDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Icon merge direction flow
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Bottom)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardDoubleArrowLeft,
                                        contentDescription = "Merge into direction",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Head/Src merge branch
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Source Head Branch", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box {
                                        OutlinedCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedHeadDropdown = true },
                                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    mergeHeadBranch.ifEmpty { "Select..." },
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1,
                                                    color = if (mergeHeadBranch.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = expandedHeadDropdown,
                                            onDismissRequest = { expandedHeadDropdown = false }
                                        ) {
                                            branches.forEach { br ->
                                                DropdownMenuItem(
                                                    text = { Text(br.name, fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        mergeHeadBranch = br.name
                                                        expandedHeadDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = mergeMessage,
                                onValueChange = { mergeMessage = it },
                                label = { Text("Merge Commit Message") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) },
                                placeholder = { Text("Merge branch '$mergeHeadBranch' into '$mergeBaseBranch'") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                            )

                            Button(
                                onClick = {
                                    val msg = mergeMessage.ifBlank { "Merge branch '$mergeHeadBranch' into '$mergeBaseBranch'" }
                                    viewModel.mergeBranches(mergeBaseBranch, mergeHeadBranch, msg)
                                    mergeMessage = ""
                                },
                                enabled = mergeBaseBranch.isNotBlank() && mergeHeadBranch.isNotBlank() && (mergeBaseBranch != mergeHeadBranch),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Merge, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Execute Merge Request")
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to create new branch cleanly in UI
    if (showCreateBranchDialog) {
        AlertDialog(
            onDismissRequest = { showCreateBranchDialog = false },
            title = { Text("Create New Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the name for a new branch branching from the currently selected branch: '$currentBranch'.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("Branch Name") },
                        modifier = Modifier.fillMaxWidth().testTag("new_branch_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createBranch(newBranchName, currentBranch)
                        currentBranch = newBranchName
                        showCreateBranchDialog = false
                    },
                    enabled = newBranchName.isNotBlank()
                ) {
                    Text("Create Branch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBranchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create Local File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter relative path and content to write this file inside the project workspace directory.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = relativePathInput,
                        onValueChange = { relativePathInput = it },
                        label = { Text("File Path (e.g. index.html, README.md)") },
                        modifier = Modifier.fillMaxWidth().testTag("add_file_path_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fileTextContentInput,
                        onValueChange = { fileTextContentInput = it },
                        label = { Text("File Content") },
                        modifier = Modifier.fillMaxWidth().height(160.dp).testTag("add_file_content_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (relativePathInput.isNotBlank()) {
                            viewModel.saveWorkspaceFile(context, relativePathInput, fileTextContentInput)
                            showCreateFileDialog = false
                        }
                    },
                    enabled = relativePathInput.isNotBlank()
                ) {
                    Text("Save File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditFileDialog) {
        AlertDialog(
            onDismissRequest = { showEditFileDialog = false },
            title = { Text("Edit File: $selectedEditingFileRelativePath") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = selectedEditingFileContent,
                        onValueChange = { selectedEditingFileContent = it },
                        label = { Text("File Content") },
                        modifier = Modifier.fillMaxWidth().height(260.dp).testTag("edit_file_content_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveWorkspaceFile(context, selectedEditingFileRelativePath, selectedEditingFileContent)
                        showEditFileDialog = false
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDate(isoString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(isoString) ?: return isoString
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        isoString
    }
}
