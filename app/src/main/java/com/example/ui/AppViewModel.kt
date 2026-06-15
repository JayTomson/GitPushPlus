package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GitProject
import com.example.data.ProjectDao
import com.example.data.SettingsRepository
import com.example.network.CreateRepoRequest
import com.example.network.GithubApiService
import com.example.network.GithubRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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

    fun saveCredentials(user: String, tok: String) {
        viewModelScope.launch {
            settingsRepository.saveCredentials(user, tok)
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            settingsRepository.clearCredentials()
            remoteRepos.value = emptyList()
        }
    }

    fun fetchRepos() {
        viewModelScope.launch {
            val currentToken = token.value
            if (currentToken.isNullOrBlank()) {
                errorMsg.value = "Token is missing"
                return@launch
            }
            isLoadingRepos.value = true
            errorMsg.value = null
            try {
                val repos = githubApi.getMyRepos("Bearer \$currentToken")
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
                    "Bearer \$currentToken",
                    CreateRepoRequest(name = repoName, isPrivate = isPrivate, autoInit = true)
                )
                // Wait briefly for init? The API creates it immediately if auto_init = true.
                addProject(appProjectName, currentUser, newRepo.name, defaultBranch) // Custom branch usually requires another API call, we stick to default for now
                fetchRepos()
            } catch (e: Exception) {
                errorMsg.value = e.localizedMessage ?: "Failed to create repository"
            } finally {
                isLoadingRepos.value = false
            }
        }
    }
    
    fun clearError() {
        errorMsg.value = null
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
