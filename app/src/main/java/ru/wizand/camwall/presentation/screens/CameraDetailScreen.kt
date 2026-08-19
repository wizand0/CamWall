package ru.wizand.camwall.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import ru.wizand.camwall.util.RtspUrlMasker
import ru.wizand.camwall.viewmodels.CameraWallViewModel
import ru.wizand.camwall.viewmodel_factory.CameraWallViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetailScreen(
    navController: NavController,
    cameraId: String,
    viewModelFactory: CameraWallViewModelFactory,
    viewModel: CameraWallViewModel = viewModel(factory = viewModelFactory)
) {
    val camera by viewModel.getCameraById(cameraId).collectAsState(initial = null)
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cameraId) {
        viewModel.loadCameraById(cameraId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(camera?.name ?: "Camera Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        android.util.Log.d("CameraDetailScreen", "Refresh clicked for ${camera?.id}")
                        // Refresh this camera's frame
                        camera?.let { viewModel.refreshCamera(it) }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    IconButton(onClick = {
                        // Navigate to edit screen
                        // For now, we'll just navigate to add screen with edit mode
                        // In a real implementation, you'd have an EditCameraScreen
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            camera?.let { cam ->
                val context = LocalContext.current
                val frameFile = context.filesDir.resolve(cam.frameFilePath)
                val frameModel: Any? = if (frameFile.exists()) frameFile else null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Camera preview
                    AsyncImage(
                        model = frameModel,
                        contentDescription = "Camera preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // Camera info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(text = "Name: ${cam.name}")
                        // Пароль в URL не показываем (ТЗ §43)
                        Text(text = "URL: ${RtspUrlMasker.mask(cam.rtspUrl)}")
                        Text(text = "Status: ${cam.status}")
                        val frameTime = cam.lastSuccessfulFrameAt?.let {
                            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "No frame yet"
                        Text(text = "Last frame: $frameTime")
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Camera") },
            text = { Text("Are you sure you want to delete this camera?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        camera?.let {
                            viewModel.deleteCamera(it)
                            navController.popBackStack()
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
