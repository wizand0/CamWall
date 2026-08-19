package ru.wizand.camwall.rtsp

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.wizand.camwall.domain.model.Frame
import java.io.File
import kotlin.coroutines.resume

/**
 * Захват одного кадра из RTSP-потока через FFmpeg (FFmpegKit).
 *
 * Почему не media3/ExoPlayer: androidx.media3 exoplayer-rtsp жёстко требует
 * атрибут `fmtp` (sprop-parameter-sets) в SDP-ответе камеры и падает с
 * IllegalArgumentException("missing attribute fmtp"), если камера его не
 * присылает (частый случай для дешёвых IP-камер/NVR). FFmpeg, как и VLC,
 * умеет доставать параметры кодека прямо из потока (из первого IDR-кадра),
 * поэтому работает с такими камерами без проблем.
 *
 * Кадр атомарно (tmp + rename) пишется в
 * files/cameras/{cameraId}/latest.jpg (ТЗ §8).
 */
class RtspFrameCapture(private val context: Context) {

    suspend fun captureFrame(
        cameraId: String,
        rtspUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Result<Frame> = withContext(Dispatchers.IO) {
        Log.d(TAG, "captureFrame start: cameraId=$cameraId")
        val result = withTimeoutOrNull(timeoutMs) { captureInternal(cameraId, rtspUrl) }
            ?: Result.failure(Exception("Timeout while capturing frame from RTSP stream"))
        Log.d(
            TAG,
            "captureFrame result: ${if (result.isSuccess) "success" else "failure: ${result.exceptionOrNull()}"}"
        )
        result
    }

    private suspend fun captureInternal(cameraId: String, rtspUrl: String): Result<Frame> {
        val cameraDir = File(context.filesDir, "cameras/$cameraId")
        if (!cameraDir.exists()) cameraDir.mkdirs()
        val target = File(cameraDir, "latest.jpg")

        // Отдельная поддиректория для серии кадров, чтобы не мешать target/tmp других вызовов.
        val burstDir = File(cameraDir, "burst_tmp")
        if (burstDir.exists()) burstDir.deleteRecursively()
        burstDir.mkdirs()
        val pattern = File(burstDir, "frame_%03d.jpg")

        val timeoutMicros = (DEFAULT_TIMEOUT_MS - 2_000L).coerceAtLeast(3_000L) * 1000L

        // Аргументы передаём массивом (а не единой командной строкой), чтобы
        // избежать проблем с экранированием спецсимволов в rtspUrl (@, :, /).
        // -skip_frame nokey (до -i) — декодер пропускает все не-ключевые кадры,
        // поэтому каждый из WARMUP_FRAMES кадров гарантированно самостоятельный
        // (не зависит от предыдущих), в отличие от возможного "битого" P-кадра сразу
        // после подключения. Берём последний из серии — на случай, если первый
        // keyframe после reconnect у конкретной камеры менее стабилен по экспозиции/фокусу.
        val args = arrayOf(
            "-y",
            "-rtsp_transport", "tcp",
            "-timeout", timeoutMicros.toString(),
            "-skip_frame", "nokey",
            "-i", rtspUrl,
            "-frames:v", WARMUP_FRAMES.toString(),
            "-q:v", "2",
            "-vf", "scale='min($MAX_SIDE_PX,iw)':-2",
            "-f", "image2",
            pattern.absolutePath
        )

        val returnCode = runFFmpeg(args)

        if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
            burstDir.deleteRecursively()
            return Result.failure(Exception("FFmpeg failed, returnCode=$returnCode"))
        }

        val lastFrame = burstDir.listFiles { f -> f.extension == "jpg" }
            ?.filter { it.length() > 0L }
            ?.maxByOrNull { it.name }

        if (lastFrame == null) {
            burstDir.deleteRecursively()
            return Result.failure(Exception("FFmpeg reported success but no output frames found"))
        }

        // Атомарная замена (ТЗ §8): копируем выбранный кадр во временный файл
        // рядом с target, затем rename поверх старого latest.jpg.
        val tmp = File(cameraDir, "latest.jpg.tmp")
        lastFrame.copyTo(tmp, overwrite = true)
        burstDir.deleteRecursively()

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }

        val bitmap = BitmapFactory.decodeFile(target.absolutePath)
            ?: return Result.failure(Exception("Captured file is not a valid image"))

        return Result.success(
            Frame(
                bitmap = bitmap,
                filePath = target.absolutePath,
                timestamp = System.currentTimeMillis(),
                width = bitmap.width,
                height = bitmap.height,
                size = target.length()
            )
        )
    }

    private suspend fun runFFmpeg(args: Array<String>): ReturnCode? =
        suspendCancellableCoroutine { cont ->
            var session: FFmpegSession? = null
            cont.invokeOnCancellation {
                session?.let { FFmpegKit.cancel(it.sessionId) }
            }
            session = FFmpegKit.executeWithArgumentsAsync(args) { completedSession ->
                if (cont.isActive) cont.resume(completedSession.returnCode)
            }
        }

    companion object {
        private const val TAG = "RtspFrameCapture"
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val MAX_SIDE_PX = 640
        // Сколько ключевых кадров подряд захватить — берём последний (самый "прогретый").
        // Каждый доп. кадр — это +1-2 сек ожидания (интервал keyframe у типичной IP-камеры).
        private const val WARMUP_FRAMES = 3
    }
}