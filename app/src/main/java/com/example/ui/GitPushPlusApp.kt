package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.GitProject
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object SettingsRoute

@Serializable
object AddProjectRoute

@Serializable
data class ProjectDetailRoute(
    val id: Int,
    val name: String,
    val repoOwner: String,
    val repoName: String,
    val defaultBranch: String
)

@Composable
fun GitPushPlusApp(viewModel: AppViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToAddProject = { navController.navigate(AddProjectRoute) },
                onProjectClick = { project ->
                    navController.navigate(
                        ProjectDetailRoute(
                            id = project.id,
                            name = project.name,
                            repoOwner = project.repoOwner,
                            repoName = project.repoName,
                            defaultBranch = project.defaultBranch
                        )
                    )
                }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<AddProjectRoute> {
            AddProjectScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable<ProjectDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ProjectDetailRoute>()
            val project = remember(route) {
                GitProject(
                    id = route.id,
                    name = route.name,
                    repoOwner = route.repoOwner,
                    repoName = route.repoName,
                    defaultBranch = route.defaultBranch
                )
            }
            ProjectDetailScreen(
                project = project,
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
