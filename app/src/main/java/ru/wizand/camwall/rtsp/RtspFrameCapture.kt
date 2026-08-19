package ru.wizand.camwall.rtsp

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.MediaItem
import kotlinx.coroutines.withContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.video.VideoSink
import androidx.media3.exoplayer.video.VideoRendererEventListener
import kotlinx.coroutines.*
import ru.wizand.camwall.domain.model.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@UnstableApi
class RtspFrameCapture(private val context: Context) { // Renamed from RtspFrameCaptureNew

    suspend fun captureFrame(cameraId: String, rtspUrl: String, timeoutMs: Long = 15000): Result<Frame> = withTimeout(timeoutMs) {
        try {
            // Создаем ExoPlayer для захвата кадра
            val player = ExoPlayer.Builder(context).build()
            
            val mediaItem = MediaItem.fromUri(rtspUrl)
            player.setMediaItem(mediaItem)
            
            // Создаем TextureView для рендеринга видео
            val textureView = android.view.TextureView(context)
            player.setVideoTextureView(textureView)
            
            // Подготовка плеера
            player.prepare()
            player.play()
            
            // Ждем получения первого кадра
            val bitmap = withContext(Dispatchers.Main) {
                waitForFrameFromTextureView(textureView, timeoutMs)
            }
            
            if (bitmap != null) {
                // Сохраняем кадр в постоянный файл камеры (files/cameras/{cameraId}/latest.jpg)
                val frameFile = saveBitmapToFile(bitmap, cameraId)
                
                // Останавливаем и освобождаем ресурсы
                player.stop()
                player.release()
                
                Result.success(Frame(
                    bitmap = bitmap,
                    filePath = frameFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    width = bitmap.width,
                    height = bitmap.height,
                    size = frameFile.length()
                ))
            } else {
                // Останавливаем и освобождаем ресурсы
                player.stop()
                player.release()
                
                Result.failure(Exception("Failed to capture frame from RTSP stream"))
            }
        } catch (e: Exception) {
            if (e is TimeoutCancellationException) {
                Result.failure(Exception("Timeout while capturing frame from RTSP stream"))
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun waitForFrameFromTextureView(textureView: TextureView, timeoutMs: Long): Bitmap? {
        return suspendCoroutine { continuation ->
            var captured = false
            var timeoutJob: Job? = null
            
            // Проверяем, готова ли текстура для захвата
            if (textureView.isAvailable) {
                // Захватываем кадр
                val bitmap = textureView.bitmap
                if (bitmap != null) {
                    captured = true
                    continuation.resume(bitmap)
                }
            }
            
            // Устанавливаем слушатель доступности поверхности
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                    // Поверхность доступна, но кадр может еще не поступить
                    // Установим небольшую задержку, чтобы дождаться первого кадра
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!captured) {
                            val bitmap = textureView.bitmap
                            if (bitmap != null && !captured) {
                                captured = true
                                timeoutJob?.cancel()
                                continuation.resume(bitmap)
                            }
                        }
                    }, 1000) // Ждем 1 секунду для получения первого кадра
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                    if (!captured) {
                        timeoutJob?.cancel()
                        continuation.resume(null)
                    }
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) {
                    // Этот метод вызывается при обновлении текстуры (новый кадр)
                    if (!captured) {
                        // Защита от частых вызовов - захватываем только первый кадр
                        val bitmap = textureView.bitmap
                        if (bitmap != null) {
                            captured = true
                            timeoutJob?.cancel()
                            continuation.resume(bitmap)
                        }
                    }
                }
            }
            
            // Устанавливаем таймаут
            timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                delay(timeoutMs)
                if (!captured) {
                    captured = true
                    try {
                        continuation.resume(null)
                    } catch (e: IllegalStateException) {
                        // Continuation might have already been resumed
                    }
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, cameraId: String): File {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

        val cameraDir = File(context.filesDir, "cameras/$cameraId")
        if (!cameraDir.exists()) {
            cameraDir.mkdirs()
        }
        val file = File(cameraDir, "latest.jpg")
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(outputStream.toByteArray())
        fileOutputStream.flush()
        fileOutputStream.close()

        return file
    }
}