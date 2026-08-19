package ru.wizand.camwall.presentation.screens

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Экран сканирования QR-кода для добавления камеры (этап A).
 *
 * Поддерживается только QR с plain-text RTSP-ссылкой (rtsp://...).
 * Результат возвращается на AddCameraScreen через savedStateHandle
 * предыдущего back stack entry (ключ SCAN_RESULT_KEY).
 *
 * Содержимое QR может содержать пароль — в логах не печатается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hint by remember { mutableStateOf("Point the camera at a QR code with an rtsp:// link") }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef?.unbindAll()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            AndroidView(
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                    cameraProviderFuture.addListener({
                        val provider = try {
                            cameraProviderFuture.get()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get CameraProvider", e)
                            hint = "Failed to start camera"
                            return@addListener
                        }
                        cameraProviderRef = provider

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyzer ->
                                analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                                    if (!handled) {
                                        processFrame(imageProxy, scanner) { value ->
                                            handled = true
                                            val trimmed = value.trim()
                                            if (trimmed.startsWith("rtsp://", ignoreCase = true)) {
                                                navController.previousBackStackEntry
                                                    ?.savedStateHandle
                                                    ?.set(SCAN_RESULT_KEY, trimmed)
                                                navController.popBackStack()
                                            } else {
                                                handled = false
                                                hint = "QR does not contain an rtsp:// link, try another one"
                                            }
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to bind camera use cases", e)
                            hint = "Failed to start camera"
                        }
                    }, ContextCompat.getMainExecutor(viewContext))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

private fun processFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onQrFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees
    )
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val qr = barcodes.firstOrNull {
                it.valueType == Barcode.TYPE_TEXT || it.format == Barcode.FORMAT_QR_CODE
            }
            qr?.rawValue?.let(onQrFound)
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Barcode scanning failed", e)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private const val TAG = "ScanQrScreen"
const val SCAN_RESULT_KEY = "scan_qr_result"
