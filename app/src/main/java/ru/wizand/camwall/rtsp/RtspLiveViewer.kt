package ru.wizand.camwall.rtsp

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import java.io.File

/**
 * Просмотр RTSP-потока в реальном времени (каждый кадр)
 * через долгоживущий сеанс FFmpegKit (этап C).
 *
 * Один сеанс на время просмотра: каждый декодированный кадр пишется в один
 * и тот же файл live.jpg (`-update 1`), который UI перечитывает по таймеру.
 * Прореживания нет — выводим каждый кадр потока.
 *
 * Почему не ExoPlayer: media3-exoplayer-rtsp требует fmtp в SDP и падает на
 * дешёвых камерах/NVR (см. RtspFrameCapture). FFmpeg работает со всеми теми же
 * камерами, что и snapshot-режим.
 *
 * Файл живёт в cacheDir (не files) и удаляется при остановке.
 * Сеанс останавливается обязательно (DisposableEffect экрана) — иначе FFmpeg
 * продолжит держать RTSP-сессию и CPU.
 */
class RtspLiveViewer(context: Context) {

    private val liveDir = File(context.cacheDir, "live").apply { mkdirs() }
    val liveFrameFile: File = File(liveDir, "live.jpg")

    private var session: FFmpegSession? = null

    val isRunning: Boolean
        get() = session != null

    /**
     * Запускает сеанс захвата. Если сеанс уже идёт — ничего не делает.
     * Возвращает файл, в который FFmpeg пишет актуальный кадр.
     */
    fun start(rtspUrl: String): File {
        if (session != null) return liveFrameFile

        if (liveFrameFile.exists()) liveFrameFile.delete()

        val args = arrayOf(
            "-y",
            "-rtsp_transport", "tcp",
            "-i", rtspUrl,
            "-q:v", "3",
            "-vf", "scale='min($MAX_SIDE_PX,iw)':-2",
            // Каждый кадр потока перезаписывает один и тот же файл.
            "-update", "1",
            "-f", "image2",
            liveFrameFile.absolutePath
        )

        Log.d(TAG, "start: live view session started")
        // executeWithArgumentsAsync: callback по завершении (остановка/ошибка).
        session = FFmpegKit.executeWithArgumentsAsync(args) { completed ->
            Log.d(TAG, "live view session finished: returnCode=${completed.returnCode}")
            session = null
        }
        return liveFrameFile
    }

    /**
     * Останавливает сеанс и удаляет временный файл.
     * Идемпотентно: можно звать из DisposableEffect без проверок.
     */
    fun stop() {
        session?.let {
            Log.d(TAG, "stop: cancelling live view session ${it.sessionId}")
            FFmpegKit.cancel(it.sessionId)
        }
        session = null
        if (liveFrameFile.exists()) liveFrameFile.delete()
    }

    companion object {
        private const val TAG = "RtspLiveViewer"
        private const val MAX_SIDE_PX = 960
    }
}
