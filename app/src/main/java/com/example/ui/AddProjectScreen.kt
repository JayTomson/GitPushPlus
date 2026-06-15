package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    LaunchedEffect(Unit) {
        viewModel.fetchRepos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Project") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMsg != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                    }
                }
            }

            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project Name (App Display)") },
                modifier = Modifier.fillMaxWidth().testTag("add_project_name_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = branchName,
                onValueChange = { branchName = it },
                label = { Text("Branch") },
                modifier = Modifier.fillMaxWidth().testTag("add_branch_input"),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode: ", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isCreatingNew,
                        onClick = { isCreatingNew = false },
                        label = { Text("Existing") }
                    )
                    FilterChip(
                        selected = isCreatingNew,
                        onClick = { isCreatingNew = true },
                        label = { Text("Create New") }
                    )
                }
            }

            if (isCreatingNew) {
                OutlinedTextField(
                    value = newRepoName,
                    onValueChange = { newRepoName = it },
                    label = { Text("New Repository Name") },
                    modifier = Modifier.fillMaxWidth().testTag("new_repo_input"),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text("Private Repository")
                }
                Button(
                    onClick = {
                        viewModel.createAndAddProject(projectName, newRepoName, isPrivate, branchName)
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = projectName.isNotBlank() && newRepoName.isNotBlank() && username != null
                ) {
                    Text("Create & Add Project")
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search My Repositories") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val filteredRepos = remoteRepos.filter { it.fullName.contains(searchQuery, ignoreCase = true) }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredRepos) { repo ->
                            ListItem(
                                headlineContent = { Text(repo.name) },
                                supportingContent = { Text(if (repo.isPrivate) "Private" else "Public") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addProject(
                                            appProjectName = projectName.ifEmpty { repo.name },
                                            repoOwner = repo.fullName.split("/")[0],
                                            repoName = repo.name,
                                            branch = branchName
                                        )
                                        onNavigateBack()
                                    }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
