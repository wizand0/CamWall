package ru.wizand.camwall.rtsp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.wizand.camwall.domain.model.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@UnstableApi
@Composable
fun RtspTestScreen() {
    var rtspUrl by remember { mutableStateOf("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4") }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var capturedFrame by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "RTSP Test Screen",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = rtspUrl,
            onValueChange = { rtspUrl = it },
            label = { Text("RTSP URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                // Запуск теста получения кадра
            },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTesting) "Testing..." else "Test RTSP Capture")
        }

        if (capturedFrame != null) {
            Text(text = "Captured Frame:")
            // Показываем изображение
        }

        if (testResult.isNotEmpty()) {
            Card {
                Text(
                    text = testResult,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}