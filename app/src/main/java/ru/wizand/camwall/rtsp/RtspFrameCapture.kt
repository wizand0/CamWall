package ru.wizand.camwall.rtsp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.*
import ru.wizand.camwall.domain.model.Frame
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@UnstableApi
class RtspFrameCapture(private val context: Context) {

    suspend fun captureFrame(rtspUrl: String, timeoutMs: Long = 10000): Result<Frame> = withTimeout(timeoutMs) {
        try {
            // Создаем ExoPlayer для захвата кадра
            val trackSelector = DefaultTrackSelector(context)
            val player = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build()

            val mediaItem = MediaItem.fromUri(rtspUrl)
            player.setMediaItem(mediaItem)
            
            // Подготовка плеера
            player.prepare()
            player.play()

            // Ждем получения первого кадра
            val bitmap = waitForFirstFrame(player, timeoutMs)
            
            if (bitmap != null) {
                // Сохраняем кадр во временный файл
                val frameFile = saveBitmapToFile(bitmap)
                
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
            Timber.e(e, "Error capturing frame from RTSP: $rtspUrl")
            Result.failure(e)
        }
    }

    private suspend fun waitForFirstFrame(player: ExoPlayer, timeoutMs: Long): Bitmap? {
        return suspendCoroutine { continuation ->
            var timeoutJob: Job? = null
            
            // Устанавливаем слушатель для получения кадра
            val listener = object : ExoPlayer.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        // Пытаемся получить кадр с задержкой для стабилизации
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1000) // Ждем немного для стабилизации потока
                            
                            // В Android Media3 нет прямого способа получить кадр как Bitmap
                            // Поэтому используем альтернативный подход
                            // Для настоящего получения кадра может потребоваться Surface
                            
                            // Здесь мы просто возвращаем null, так как Media3 не предоставляет
                            // простого способа получить кадр как Bitmap
                            // Реализация будет зависеть от конкретного подхода
                            timeoutJob?.cancel()
                            continuation.resume(null)
                        }
                    }
                }
            }
            
            player.addListener(listener)
            
            // Устанавливаем таймаут
            timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                delay(timeoutMs)
                if (continuation.isActive) {
                    player.removeListener(listener)
                    continuation.resume(null)
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        
        val file = File(context.cacheDir, "temp_frame_${System.currentTimeMillis()}.jpg")
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(outputStream.toByteArray())
        fileOutputStream.flush()
        fileOutputStream.close()
        
        return file
    }
}