package ru.wizand.camwall.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.model.CameraStatus
import ru.wizand.camwall.ui.components.CameraCard
import ru.wizand.camwall.ui.viewmodels.CameraWallViewModel

@Composable
fun CameraWallScreen(
    viewModel: CameraWallViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val cameras by viewModel.cameras
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Navigate to Add Camera */ }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Camera")
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(cameras) { camera ->
                CameraCard(
                    camera = camera,
                    onCameraClick = { /* TODO: Navigate to Camera Detail */ }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CameraWallScreenPreview() {
    // Заглушка для предварительного просмотра
    val mockCameras = listOf(
        Camera(
            id = "1",
            name = "Вход в лобби - арка",
            rtspUrl = "rtsp://example.com/cam1",
            lastSuccessfulFrameAt = System.currentTimeMillis() - 30000, // 30 секунд назад
            lastError = null,
            consecutiveErrors = 0
        ),
        Camera(
            id = "2",
            name = "Камера 2",
            rtspUrl = "rtsp://example.com/cam2",
            lastSuccessfulFrameAt = System.currentTimeMillis() - 300000, // 5 минут назад
            lastError = "Network timeout",
            consecutiveErrors = 3
        )
    )
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(mockCameras) { camera ->
            CameraCard(
                camera = camera,
                onCameraClick = {}
            )
        }
    }
}