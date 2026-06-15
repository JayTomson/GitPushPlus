package com.example.ui

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GitProject
import com.example.data.ProjectDao
import com.example.data.SettingsRepository
import com.example.network.*
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val projectDao: ProjectDao,
    private val settingsRepository: SettingsRepository,
    private val githubApi: GithubApiService
) : ViewModel() {

    val username = settingsRepository.usernameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val token = settingsRepository.tokenFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val projects: StateFlow<List<GitProject>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val remoteRepos = MutableStateFlow<List<GithubRepo>>(emptyList())
    val isLoadingRepos = MutableStateFlow(false)
    val errorMsg = MutableStateFlow<String?>(null)
    val gitActionSuccessMsg = MutableStateFlow<String?>(null)

    // Git project details state
    val selectedProject = MutableStateFlow<GitProject?>(null)
    val selectedBranch = MutableStateFlow<String>("main")
    val projectBranches = MutableStateFlow<List<GithubBranch>>(emptyList())
    val projectCommits = MutableStateFlow<List<GithubCommit>>(emptyList())
    val isLoadingBranches = MutableStateFlow(false)
    val isLoadingCommits = MutableStateFlow(false)

    // Workspace & Change Management
    val localFiles = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val remoteGitTree = MutableStateFlow<List<com.example.network.GitTreeEntry>>(emptyList())
    val modifiedFiles = MutableStateFlow<List<LocalModifiedFile>>(emptyList())
    val isLoadingWorkspace = MutableStateFlow(false)

    fun saveCredentials(user: String, tok: String) {
        viewModelScope.launch {
            settingsRepository.saveCredentials(user, tok)
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            settingsRepository.clearCredentials()
            remoteRepos.value = emptyList()
            projectBranches.value = emptyList()
            projectCommits.value = emptyList()
            selectedProject.value = null
        }
    }

    fun fetchRepos() {
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Token is missing. Connect your GitHub account in Settings."
                return@launch
            }
            isLoadingRepos.value = true
            errorMsg.value = null
            try {
                val repos = githubApi.getMyRepos("Bearer $currentToken")
                remoteRepos.value = repos
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to fetch repositories"
            } finally {
                isLoadingRepos.value = false
            }
        }
    }

    fun addProject(appProjectName: String, repoOwner: String, repoName: String, branch: String, localFolderPath: String = "") {
        viewModelScope.launch {
            val pathValue = localFolderPath.ifBlank { "Workspace/$repoName" }
            projectDao.insertProject(
                GitProject(
                    name = appProjectName,
                    repoOwner = repoOwner,
                    repoName = repoName,
                    defaultBranch = branch,
                    localFolderPath = pathValue
                )
            )
        }
    }

    fun deleteProject(projectId: Int) {
        viewModelScope.launch {
            projectDao.deleteProjectById(projectId)
            if (selectedProject.value?.id == projectId) {
                selectedProject.value = null
            }
        }
    }

    fun createAndAddProject(appProjectName: String, repoName: String, isPrivate: Boolean, defaultBranch: String, localFolderPath: String = "") {
        viewModelScope.launch {
            val currentToken = token.value
            val currentUser = username.value
            if (currentToken.isNullOrBlank() || currentUser.isNullOrBlank()) {
                errorMsg.value = "Token or username missing"
                return@launch
            }
            isLoadingRepos.value = true
            errorMsg.value = null
            try {
                val newRepo = githubApi.createRepo(
                    "Bearer $currentToken",
                    CreateRepoRequest(name = repoName, isPrivate = isPrivate, autoInit = true)
                )
                // Add the project locally with the specified branch
                addProject(appProjectName, currentUser, newRepo.name, defaultBranch, localFolderPath)
                gitActionSuccessMsg.value = "Repository '$repoName' created successfully!"
                fetchRepos()
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to create repository"
            } finally {
                isLoadingRepos.value = false
            }
        }
    }

    fun selectProject(project: GitProject) {
        selectedProject.value = project
        selectedBranch.value = project.defaultBranch
        fetchBranchesAndCommits()
    }

    fun selectBranch(branchName: String) {
        selectedBranch.value = branchName
        val project = selectedProject.value
        if (project != null) {
            fetchCommits(project.repoOwner, project.repoName, branchName)
        }
    }

    fun updateProjectLocalFolderPath(context: android.content.Context, path: String) {
        val current = selectedProject.value ?: return
        viewModelScope.launch {
            val updated = current.copy(localFolderPath = path)
            projectDao.insertProject(updated)
            selectedProject.value = updated
            scanLocalWorkspace(context)
        }
    }

    fun fetchBranchesAndCommits() {
        val project = selectedProject.value ?: return
        fetchBranches(project.repoOwner, project.repoName)
        fetchCommits(project.repoOwner, project.repoName, selectedBranch.value)
    }

    fun fetchBranches(owner: String, repo: String) {
        viewModelScope.launch {
            val currentToken = token.value ?: return@launch
            isLoadingBranches.value = true
            try {
                val branches = githubApi.getBranches("Bearer $currentToken", owner, repo)
                projectBranches.value = branches
            } catch (e: Exception) {
                // If there's an error, default to default branch
                projectBranches.value = listOf(GithubBranch(name = selectedBranch.value))
            } finally {
                isLoadingBranches.value = false
            }
        }
    }

    fun fetchCommits(owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            val currentToken = token.value ?: return@launch
            isLoadingCommits.value = true
            try {
                val commits = githubApi.getCommits("Bearer $currentToken", owner, repo, branch)
                projectCommits.value = commits
            } catch (e: Exception) {
                projectCommits.value = emptyList()
            } finally {
                isLoadingCommits.value = false
            }
        }
    }

    fun createBranch(newBranchName: String, baseBranch: String) {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Auth token missing"
                return@launch
            }
            try {
                // 1. Get basis branch's commit SHA
                val branches = githubApi.getBranches("Bearer $currentToken", project.repoOwner, project.repoName)
                val baseRef = branches.firstOrNull { it.name == baseBranch }
                val baseSha = baseRef?.commit?.sha
                if (baseSha.isNullOrBlank()) {
                    errorMsg.value = "Could not find SHA for branch '$baseBranch'"
                    return@launch
                }

                // 2. Create the new ref
                val response = githubApi.createBranchRef(
                    "Bearer $currentToken",
                    project.repoOwner,
                    project.repoName,
                    CreateBranchRefRequest(ref = "refs/heads/$newBranchName", sha = baseSha)
                )

                if (response.isSuccessful) {
                    gitActionSuccessMsg.value = "Branch '$newBranchName' successfully created!"
                    fetchBranches(project.repoOwner, project.repoName)
                    selectBranch(newBranchName)
                } else {
                    errorMsg.value = "Failed to create branch: " + (response.errorBody()?.string() ?: "Unknown error")
                }
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to create branch"
            }
        }
    }

    fun mergeBranches(baseBranch: String, headBranch: String, commitMessage: String) {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Auth token missing"
                return@launch
            }
            try {
                val response = githubApi.mergeBranch(
                    "Bearer $currentToken",
                    project.repoOwner,
                    project.repoName,
                    MergeRequest(base = baseBranch, head = headBranch, commitMessage = commitMessage)
                )
                if (response.isSuccessful) {
                    gitActionSuccessMsg.value = "Branch '$headBranch' merged into '$baseBranch' successfully!"
                    fetchBranchesAndCommits()
                } else {
                    val code = response.code()
                    val errorDetail = response.errorBody()?.string() ?: ""
                    if (code == 409) {
                        errorMsg.value = "Merge conflicts detected. Cannot merge cleanly."
                    } else if (code == 204) {
                        gitActionSuccessMsg.value = "Nothing to merge (branches already synchronized)."
                    } else {
                        errorMsg.value = "Merge failed (${code}): $errorDetail"
                    }
                }
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to merge branches"
            }
        }
    }

    fun commitFile(path: String, content: String, message: String) {
        val project = selectedProject.value ?: return
        val currentBranch = selectedBranch.value
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Auth token missing"
                return@launch
            }
            try {
                // 1. Check if the file already exists on this branch to get its SHA
                var sha: String? = null
                val fileInfoResponse = githubApi.getFileContent(
                    "Bearer $currentToken",
                    project.repoOwner,
                    project.repoName,
                    path,
                    currentBranch
                )
                if (fileInfoResponse.isSuccessful) {
                    sha = fileInfoResponse.body()?.sha
                }

                // 2. Base64 encode file content
                val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                // 3. Create or update file
                githubApi.createOrUpdateFile(
                    "Bearer $currentToken",
                    project.repoOwner,
                    project.repoName,
                    path,
                    CreateOrUpdateFileRequest(
                        message = message,
                        content = base64Content,
                        branch = currentBranch,
                        sha = sha
                    )
                )

                gitActionSuccessMsg.value = "File '$path' successfully committed to '$currentBranch'!"
                fetchCommits(project.repoOwner, project.repoName, currentBranch)
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to commit changes"
            }
        }
    }

    fun clearError() {
        errorMsg.value = null
    }

    fun clearSuccessMsg() {
        gitActionSuccessMsg.value = null
    }

    class Factory(
        private val projectDao: ProjectDao,
        private val settingsRepository: SettingsRepository,
        private val githubApi: GithubApiService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(projectDao, settingsRepository, githubApi) as T
        }
    }

    fun scanLocalWorkspace(context: android.content.Context) {
        val project = selectedProject.value ?: return
        val path = project.localFolderPath
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isLoadingWorkspace.value = true
            val collectedFiles = mutableListOf<WorkspaceFile>()

            if (path.startsWith("content://")) {
                // SAF DocumentTree Mode
                try {
                    val parsedUri = Uri.parse(path)
                    val rootDir = DocumentFile.fromTreeUri(context, parsedUri)
                    if (rootDir != null && rootDir.exists()) {
                        walkDocumentTree(context, rootDir, rootDir, "", collectedFiles)
                    }
                } catch (e: Exception) {
                    errorMsg.value = "Failed to scan authorized system directory: " + e.localizedMessage
                }
            } else {
                // Legacy / FilesDir Mode
                val workspaceDir = java.io.File(context.filesDir, path)
                if (!workspaceDir.exists()) {
                    workspaceDir.mkdirs()
                }
                val files = workspaceDir.walkTopDown()
                    .filter { it.isFile && !it.absolutePath.contains("/.") && !it.name.startsWith(".") }
                    .toList()

                for (file in files) {
                    val relativePath = file.relativeTo(workspaceDir).path.replace("\\", "/")
                    collectedFiles.add(
                        WorkspaceFile(
                            name = file.name,
                            relativePath = relativePath,
                            size = file.length(),
                            file = file
                        )
                    )
                }
            }

            localFiles.value = collectedFiles

            // Compute modified files
            val tree = remoteGitTree.value
            val modifiedList = mutableListOf<LocalModifiedFile>()

            for (file in collectedFiles) {
                val content = if (file.uriString != null) {
                    readDocContent(context, Uri.parse(file.uriString))
                } else {
                    try { file.file?.readText(Charsets.UTF_8) ?: "" } catch (e: Exception) { "" }
                }
                val localSha = computeGitSha(content)

                val remoteEntry = tree.firstOrNull { it.path == file.relativePath }
                if (remoteEntry == null) {
                    // Not on remote branch -> new file
                    modifiedList.add(LocalModifiedFile(file.relativePath, file.file, isNew = true, uriString = file.uriString))
                } else if (remoteEntry.sha != localSha) {
                    // Different SHA -> modified file
                    modifiedList.add(LocalModifiedFile(file.relativePath, file.file, isNew = false, remoteSha = remoteEntry.sha, uriString = file.uriString))
                }
            }
            modifiedFiles.value = modifiedList
            isLoadingWorkspace.value = false
        }
    }

    private fun walkDocumentTree(
        context: android.content.Context,
        root: DocumentFile,
        currentDir: DocumentFile,
        parentPath: String,
        accumulatedFiles: MutableList<WorkspaceFile>
    ) {
        val childFiles = currentDir.listFiles()
        for (child in childFiles) {
            val name = child.name ?: continue
            // Skip hidden stuff and major build/node items
            if (name.startsWith(".") || name == "node_modules" || name == "build") continue
            
            val relPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
            if (child.isDirectory) {
                walkDocumentTree(context, root, child, relPath, accumulatedFiles)
            } else if (child.isFile) {
                accumulatedFiles.add(
                    WorkspaceFile(
                        name = name,
                        relativePath = relPath,
                        size = child.length(),
                        uriString = child.uri.toString()
                    )
                )
            }
        }
    }

    private fun readDocContent(context: android.content.Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun writeDocContent(context: android.content.Context, uri: Uri, text: String) {
        try {
            context.contentResolver.openOutputStream(uri, "rwt")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).write(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOrCreateDocumentFile(
        context: android.content.Context,
        treeUriStr: String,
        relativePath: String
    ): DocumentFile? {
        val rootUri = Uri.parse(treeUriStr)
        var current: DocumentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val parts = relativePath.split("/").filter { it.isNotBlank() && it != "." && it != ".." }
        if (parts.isEmpty()) return null
        
        // Traverse to build subfolders
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            var nextDir = current.findFile(part)
            if (nextDir == null || !nextDir.isDirectory) {
                nextDir = current.createDirectory(part)
            }
            if (nextDir == null) return null
            current = nextDir
        }
        
        val fileName = parts.last()
        var targetFile = current.findFile(fileName)
        if (targetFile == null || !targetFile.isFile) {
            val mimeType = getMimeTypeForName(fileName)
            targetFile = current.createFile(mimeType, fileName)
        }
        return targetFile
    }

    private fun getMimeTypeForName(name: String): String {
        return when {
            name.endsWith(".txt", true) -> "text/plain"
            name.endsWith(".html", true) -> "text/html"
            name.endsWith(".css", true) -> "text/css"
            name.endsWith(".js", true) -> "application/javascript"
            name.endsWith(".json", true) -> "application/json"
            name.endsWith(".xml", true) -> "text/xml"
            name.endsWith(".kt", true) -> "text/plain"
            name.endsWith(".md", true) -> "text/markdown"
            else -> "application/octet-stream"
        }
    }

    private fun computeGitSha(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val gitHeader = "blob ${bytes.size}\u0000"
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val headerBytes = gitHeader.toByteArray(Charsets.UTF_8)
        digest.update(headerBytes)
        digest.update(bytes)
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun syncWorkspaceFromGitHub(context: android.content.Context) {
        val project = selectedProject.value ?: return
        val branch = selectedBranch.value
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Auth token missing. Connect your account."
                return@launch
            }
            isLoadingWorkspace.value = true
            errorMsg.value = null
            try {
                // 1. Fetch remote tree
                val branchesInfo = githubApi.getBranches("Bearer $currentToken", project.repoOwner, project.repoName)
                val activeBranchCommitInfo = branchesInfo.firstOrNull { it.name == branch }
                val targetSha = activeBranchCommitInfo?.commit?.sha
                if (targetSha.isNullOrBlank()) {
                    errorMsg.value = "Cannot find SHA for branch '$branch'"
                    return@launch
                }

                val response = githubApi.getGitTree("Bearer $currentToken", project.repoOwner, project.repoName, targetSha, recursive = 1)
                if (response.isSuccessful) {
                    val treeEntries = response.body()?.tree ?: emptyList()
                    remoteGitTree.value = treeEntries

                    // 2. Overwrite / Download files
                    for (entry in treeEntries.filter { it.type == "blob" }) {
                        val fileResponse = githubApi.getFileContent("Bearer $currentToken", project.repoOwner, project.repoName, entry.path, branch)
                        if (fileResponse.isSuccessful) {
                            val base64Content = fileResponse.body()?.content?.replace("\n", "")?.replace("\r", "")?.replace(" ", "") ?: ""
                            val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                            val decodedString = String(decodedBytes, Charsets.UTF_8)
                            
                            val path = project.localFolderPath
                            if (path.startsWith("content://")) {
                                val docFile = getOrCreateDocumentFile(context, path, entry.path)
                                if (docFile != null) {
                                    writeDocContent(context, docFile.uri, decodedString)
                                }
                            } else {
                                val workspaceDir = java.io.File(context.filesDir, path)
                                val localFile = java.io.File(workspaceDir, entry.path)
                                localFile.parentFile?.mkdirs()
                                localFile.writeText(decodedString, Charsets.UTF_8)
                            }
                        }
                    }

                    gitActionSuccessMsg.value = "Workspace synchronized successfully from branch '$branch'!"
                    scanLocalWorkspace(context)
                } else {
                    errorMsg.value = "Failed to sync remote tree: " + (response.errorBody()?.string() ?: "Unknown error")
                }
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to sync workspace"
            } finally {
                isLoadingWorkspace.value = false
            }
        }
    }

    fun saveWorkspaceFile(context: android.content.Context, relativePath: String, content: String) {
        val project = selectedProject.value ?: return
        val path = project.localFolderPath
        try {
            if (path.startsWith("content://")) {
                val docFile = getOrCreateDocumentFile(context, path, relativePath)
                if (docFile != null) {
                    writeDocContent(context, docFile.uri, content)
                    scanLocalWorkspace(context)
                } else {
                    errorMsg.value = "Could not create or authorize destination file paths inside tree."
                }
            } else {
                val workspaceDir = java.io.File(context.filesDir, path)
                val targetFile = java.io.File(workspaceDir, relativePath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(content, Charsets.UTF_8)
                scanLocalWorkspace(context)
            }
        } catch (e: Exception) {
            errorMsg.value = "Failed to save file: " + e.localizedMessage
        }
    }

    fun deleteWorkspaceFile(context: android.content.Context, relativePath: String) {
        val project = selectedProject.value ?: return
        val path = project.localFolderPath
        try {
            if (path.startsWith("content://")) {
                val rootUri = Uri.parse(path)
                val rootDir = DocumentFile.fromTreeUri(context, rootUri)
                val parts = relativePath.split("/").filter { it.isNotBlank() }
                var current = rootDir
                for (part in parts) {
                    current = current?.findFile(part)
                }
                current?.delete()
                scanLocalWorkspace(context)
            } else {
                val workspaceDir = java.io.File(context.filesDir, path)
                val targetFile = java.io.File(workspaceDir, relativePath)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                scanLocalWorkspace(context)
            }
        } catch (e: Exception) {
            errorMsg.value = "Failed to delete file: " + e.localizedMessage
        }
    }

    fun pushModifiedFiles(context: android.content.Context, filesToPush: List<LocalModifiedFile>, commitMessage: String) {
        val project = selectedProject.value ?: return
        val branch = selectedBranch.value
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Auth token missing"
                return@launch
            }
            isLoadingWorkspace.value = true
            errorMsg.value = null
            try {
                var pushedCount = 0
                for (modFile in filesToPush) {
                    val relativePath = modFile.relativePath
                    
                    val content = if (modFile.uriString != null) {
                        readDocContent(context, Uri.parse(modFile.uriString))
                    } else {
                        val lf = modFile.localFile
                        if (lf != null && lf.exists()) {
                            lf.readText(Charsets.UTF_8)
                        } else {
                            continue
                        }
                    }

                    val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                    // 1. Get SHA of file if it exists remote (to update it)
                    var currentSha = modFile.remoteSha
                    if (currentSha == null) {
                        val fileInfoResponse = githubApi.getFileContent(
                            "Bearer $currentToken",
                            project.repoOwner,
                            project.repoName,
                            relativePath,
                            branch
                        )
                        if (fileInfoResponse.isSuccessful) {
                            currentSha = fileInfoResponse.body()?.sha
                        }
                    }

                    // 2. Commit & upload to Git
                    githubApi.createOrUpdateFile(
                        "Bearer $currentToken",
                        project.repoOwner,
                        project.repoName,
                        relativePath,
                        CreateOrUpdateFileRequest(
                            message = "$commitMessage (pushed: $relativePath)",
                            content = base64Content,
                            branch = branch,
                            sha = currentSha
                        )
                    )
                    pushedCount++
                }

                gitActionSuccessMsg.value = "Pushed $pushedCount modified file(s) cleanly to '$branch'!"
                fetchCommits(project.repoOwner, project.repoName, branch)
                
                // Re-sync Git tree metadata to update SHA baselines
                val branchesInfo = githubApi.getBranches("Bearer $currentToken", project.repoOwner, project.repoName)
                val activeBranchCommitInfo = branchesInfo.firstOrNull { it.name == branch }
                val targetSha = activeBranchCommitInfo?.commit?.sha
                if (!targetSha.isNullOrBlank()) {
                    val response = githubApi.getGitTree("Bearer $currentToken", project.repoOwner, project.repoName, targetSha, recursive = 1)
                    if (response.isSuccessful) {
                        remoteGitTree.value = response.body()?.tree ?: emptyList()
                    }
                }
                
                scanLocalWorkspace(context)
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to push files"
            } finally {
                isLoadingWorkspace.value = false
            }
        }
    }
}

data class WorkspaceFile(
    val name: String,
    val relativePath: String,
    val size: Long,
    val uriString: String? = null,
    val file: java.io.File? = null
)

data class LocalModifiedFile(
    val relativePath: String,
    val localFile: java.io.File? = null,
    val isNew: Boolean,
    val remoteSha: String? = null,
    val uriString: String? = null
)
