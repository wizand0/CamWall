package ru.wizand.camwall.rtsp
//
//import android.content.Context
//import android.util.Log
//import android.graphics.Bitmap
//import android.graphics.ImageFormat
//import android.graphics.PixelFormat
//import android.media.Image
//import android.media.ImageReader
//import android.os.Handler
//import android.os.Looper
//import androidx.annotation.OptIn
//import androidx.media3.common.MediaItem
//import androidx.media3.common.PlaybackException
//import androidx.media3.common.Player
//import androidx.media3.common.VideoSize
//import androidx.media3.common.util.UnstableApi
//import androidx.media3.exoplayer.ExoPlayer
//import androidx.media3.exoplayer.rtsp.RtspMediaSource
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import kotlinx.coroutines.withTimeoutOrNull
//import ru.wizand.camwall.domain.model.Frame
//import ru.wizand.camwall.util.RtspUrlMasker
//import java.io.File
//import java.io.FileOutputStream
//import kotlin.coroutines.resume
//import kotlin.math.max
//import kotlin.math.roundToInt
//
///**
// * Захват одного кадра из RTSP-потока без View-иерархии.
// *
// * Схема: ExoPlayer рендерит видео в Surface от ImageReader (RGBA_8888).
// * Первый полученный Image конвертируется в Bitmap, даунскейлится до ~640px
// * по большей стороне и атомарно (tmp + rename) пишется в
// * files/cameras/{cameraId}/latest.jpg (ТЗ §8).
// *
// * Работает и в UI-потоке, и в WorkManager. RTSP идёт по TCP
// * (setForceUseRtpTcp) — надёжнее для камер за NAT, где теряется UDP.
// */
//@OptIn(UnstableApi::class)
//class RtspFrameCapture_old(private val context: Context) {
//
//    suspend fun captureFrame(
//        cameraId: String,
//        rtspUrl: String,
//        timeoutMs: Long = DEFAULT_TIMEOUT_MS
//    ): Result<Frame> = withContext(Dispatchers.Main) {
//        Log.d(TAG, "captureFrame start: cameraId=$cameraId, url=${RtspUrlMasker.mask(rtspUrl)}")
//        val result = withTimeoutOrNull(timeoutMs) { captureInternal(cameraId, rtspUrl) }
//            ?: Result.failure(Exception("Timeout while capturing frame from RTSP stream"))
//        Log.d(TAG, "captureFrame result: ${if (result.isSuccess) "success" else "failure: ${result.exceptionOrNull()}"}")
//        result
//    }
//
//    private suspend fun captureInternal(cameraId: String, rtspUrl: String): Result<Frame> =
//        suspendCancellableCoroutine { cont ->
//            val player = ExoPlayer.Builder(context).build()
//            var imageReader: ImageReader? = null
//            var captured = false
//
//            fun releaseAll() {
//                try {
//                    player.release()
//                } catch (_: Exception) {
//                }
//                try {
//                    imageReader?.close()
//                } catch (_: Exception) {
//                }
//            }
//
//            cont.invokeOnCancellation { releaseAll() }
//
//            player.addListener(object : Player.Listener {
//                override fun onVideoSizeChanged(videoSize: VideoSize) {
//                    Log.d(TAG, "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
//                    if (captured || videoSize.width <= 0 || videoSize.height <= 0) return
//                    val reader = ImageReader.newInstance(
//                        videoSize.width,
//                        videoSize.height,
//                        PixelFormat.RGBA_8888,
//                        2
//                    )
//                    imageReader = reader
//                    reader.setOnImageAvailableListener({ r ->
//                        if (captured) {
//                            r.acquireLatestImage()?.close()
//                            return@setOnImageAvailableListener
//                        }
//                        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
//                        captured = true
//                        try {
//                            val bitmap = imageToBitmap(image)
//                            image.close()
//                            val scaled = scaleDown(bitmap)
//                            if (scaled !== bitmap) bitmap.recycle()
//                            val file = saveBitmapToFile(scaled, cameraId)
//                            val frame = Frame(
//                                bitmap = scaled,
//                                filePath = file.absolutePath,
//                                timestamp = System.currentTimeMillis(),
//                                width = scaled.width,
//                                height = scaled.height,
//                                size = file.length()
//                            )
//                            releaseAll()
//                            if (cont.isActive) cont.resume(Result.success(frame))
//                        } catch (e: Exception) {
//                            releaseAll()
//                            if (cont.isActive) cont.resume(Result.failure(e))
//                        }
//                    }, Handler(Looper.getMainLooper()))
//                    player.setVideoSurface(reader.surface)
//                }
//
//                override fun onPlayerError(error: PlaybackException) {
//                    Log.e(TAG, "onPlayerError: code=${error.errorCodeName}, msg=${error.message}", error)
//                    releaseAll()
//                    if (cont.isActive) cont.resume(Result.failure(error))
//                }
//
//                override fun onPlaybackStateChanged(state: Int) {
//                    Log.d(TAG, "onPlaybackStateChanged: $state") // 1=IDLE 2=BUFFERING 3=READY 4=ENDED
//                }
//            })
//
//            val mediaSource = RtspMediaSource.Factory()
//                .setForceUseRtpTcp(true)
//                .createMediaSource(MediaItem.fromUri(rtspUrl))
//            player.setMediaSource(mediaSource)
//            player.prepare()
//            player.play()
//        }
//
//    private fun imageToBitmap(image: Image): Bitmap {
//        val plane = image.planes[0]
//        val buffer = plane.buffer
//        val rowStride = plane.rowStride
//        val pixelStride = plane.pixelStride
//        val width = image.width
//        val height = image.height
//        val pixels = IntArray(width * height)
//        val row = ByteArray(rowStride)
//        for (y in 0 until height) {
//            buffer.position(y * rowStride)
//            buffer.get(row, 0, rowStride)
//            for (x in 0 until width) {
//                val offset = x * pixelStride
//                val r = row[offset].toInt() and 0xFF
//                val g = row[offset + 1].toInt() and 0xFF
//                val b = row[offset + 2].toInt() and 0xFF
//                val a = if (pixelStride >= 4) row[offset + 3].toInt() and 0xFF else 0xFF
//                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
//            }
//        }
//        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
//    }
//
//    private fun scaleDown(bitmap: Bitmap, maxSide: Int = MAX_SIDE_PX): Bitmap {
//        val largest = max(bitmap.width, bitmap.height)
//        if (largest <= maxSide) return bitmap
//        val scale = maxSide.toFloat() / largest
//        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
//        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
//        return Bitmap.createScaledBitmap(bitmap, w, h, true)
//    }
//
//    /** Атомарная запись: сначала во временный файл, затем rename (ТЗ §8). */
//    private fun saveBitmapToFile(bitmap: Bitmap, cameraId: String): File {
//        val cameraDir = File(context.filesDir, "cameras/$cameraId")
//        if (!cameraDir.exists()) cameraDir.mkdirs()
//        val target = File(cameraDir, "latest.jpg")
//        val tmp = File(cameraDir, "latest.jpg.tmp")
//        FileOutputStream(tmp).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
//        }
//        if (!tmp.renameTo(target)) {
//            tmp.copyTo(target, overwrite = true)
//            tmp.delete()
//        }
//        return target
//    }
//
//    companion object {
//
//        private const val TAG = "RtspFrameCapture"
//        private const val DEFAULT_TIMEOUT_MS = 15_000L
//        private const val MAX_SIDE_PX = 640
//        private const val JPEG_QUALITY = 80
//    }
//}
