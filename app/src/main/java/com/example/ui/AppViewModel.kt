package com.example.ui

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GitProject
import com.example.data.ProjectDao
import com.example.data.SettingsRepository
import com.example.network.*
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

    fun addProject(appProjectName: String, repoOwner: String, repoName: String, branch: String) {
        viewModelScope.launch {
            projectDao.insertProject(
                GitProject(
                    name = appProjectName,
                    repoOwner = repoOwner,
                    repoName = repoName,
                    defaultBranch = branch
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

    fun createAndAddProject(appProjectName: String, repoName: String, isPrivate: Boolean, defaultBranch: String) {
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
                addProject(appProjectName, currentUser, newRepo.name, defaultBranch)
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
}
