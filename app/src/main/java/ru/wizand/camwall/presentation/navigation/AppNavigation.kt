package ru.wizand.camwall.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.wizand.camwall.presentation.screens.CameraWallScreen
import ru.wizand.camwall.presentation.screens.AddCameraScreen
import ru.wizand.camwall.presentation.screens.CameraDetailScreen
import ru.wizand.camwall.presentation.screens.SettingsScreen
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory

@Composable
fun AppNavigation(viewModelFactory: CameraWallViewModelFactory) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "camera_wall"
    ) {
        composable("camera_wall") {
            CameraWallScreen(navController = navController, viewModelFactory = viewModelFactory)
        }
        composable("add_camera") {
            AddCameraScreen(navController = navController, viewModelFactory = viewModelFactory)
        }
        composable("camera_detail/{cameraId}") { backStackEntry ->
            val cameraId = backStackEntry.arguments?.getString("cameraId") ?: ""
            CameraDetailScreen(navController = navController, cameraId = cameraId, viewModelFactory = viewModelFactory)
        }
        composable("settings") {
            SettingsScreen(navController = navController, viewModelFactory = viewModelFactory)
        }
    }
}