package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.GithubRepo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val remoteRepos by viewModel.remoteRepos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingRepos.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMsg.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    var projectName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("main") }
    var searchQuery by remember { mutableStateOf("") }

    var isCreatingNew by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    var selectedRepo by remember { mutableStateOf<GithubRepo?>(null) }
    var localFolderName by remember { mutableStateOf("") }

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
            localFolderName = uri.toString()
        }
    }

    val displayFolderName = remember(localFolderName) {
        if (localFolderName.startsWith("content://")) {
            try {
                val parsedUri = android.net.Uri.parse(localFolderName)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, parsedUri)
                docFile?.name ?: "System Folder"
            } catch (e: Exception) {
                "System Folder"
            }
        } else {
            localFolderName.ifBlank { "Not selected" }
        }
    }

    LaunchedEffect(username) {
        if (username != null) {
            viewModel.fetchRepos()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Project Container", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error Alert Dialog banner
            AnimatedVisibility(visible = errorMsg != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (username.isNullOrBlank()) {
                // Connection Guidance Box
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Not Connected to GitHub",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Connecting your personal GitHub account is required before we can fetch your repositories or create projects on your behalf.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onNavigateBack() }, // Return or let settings handle it
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configure GitHub Settings Now")
                    }
                }
            } else {
                // Form Fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App display project name
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Display Name of Project") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_project_name_input"),
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        placeholder = { Text("My Awesome Project") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_local_folder_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "On-Device Folder Workspace",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = displayFolderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (localFolderName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Button(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("select_device_folder_btn")
                                ) {
                                    Text(if (localFolderName.isBlank()) "Select" else "Change")
                                }
                            }
                            if (localFolderName.startsWith("content://")) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Connected via Storage Access Framework. Persistent permission is active.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tap 'Select' to authorize any folder on your device to scan and modify standard project files.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Selection Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Add Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FilterChip(
                            selected = !isCreatingNew,
                            onClick = { isCreatingNew = false },
                            label = { Text("Existing Repository") }
                        )
                        FilterChip(
                            selected = isCreatingNew,
                            onClick = { isCreatingNew = true },
                            label = { Text("Create New Repository") }
                        )
                    }
                }

                if (isCreatingNew) {
                    // Form fields for creating a brand new GitHub repo
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = newRepoName,
                            onValueChange = { 
                                newRepoName = it
                                if (projectName.isBlank() || projectName == it.dropLast(1)) {
                                    projectName = it
                                }
                                if (localFolderName.isBlank() || localFolderName == "Workspace/${it.dropLast(1)}") {
                                    localFolderName = "Workspace/$it"
                                }
                            },
                            label = { Text("New GitHub Repo Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_repo_input"),
                            leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )

                        // Privacy settings
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Private Repository", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            if (isPrivate) "Only you and collaborators" else "Anyone can view this repo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                viewModel.createAndAddProject(
                                    appProjectName = projectName.ifBlank { newRepoName },
                                    repoName = newRepoName,
                                    isPrivate = isPrivate,
                                    localFolderPath = localFolderName.ifBlank { "Workspace/$newRepoName" }
                                )
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = projectName.isNotBlank() && newRepoName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create & Sync Repository")
                        }
                    }
                } else {
                    // Search bar on top
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search GitHub Repositories") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                    )

                    if (isLoading) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val filteredRepos = remoteRepos.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            it.fullName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredRepos.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No repositories found.\nPull again or connect your account.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(filteredRepos) { repo ->
                                    val isRepoSelected = selectedRepo?.id == repo.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                selectedRepo = repo
                                                if (projectName.isBlank() || projectName == selectedRepo?.name) {
                                                    projectName = repo.name
                                                }
                                                if (localFolderName.isBlank() || localFolderName == "Workspace/${selectedRepo?.name}") {
                                                    localFolderName = "Workspace/${repo.name}"
                                                }
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isRepoSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isRepoSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Share,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(repo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(repo.fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (isRepoSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val repo = selectedRepo
                            if (repo != null) {
                                viewModel.addProject(
                                    appProjectName = projectName.ifBlank { repo.name },
                                    repoOwner = repo.fullName.split("/")[0],
                                    repoName = repo.name,
                                    branch = repo.defaultBranch ?: "main",
                                    localFolderPath = localFolderName.ifBlank { "Workspace/${repo.name}" }
                                )
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(bottom = 8.dp),
                        enabled = projectName.isNotBlank() && selectedRepo != null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Project to Home Workspace")
                    }
                }
            }
        }
    }
}
