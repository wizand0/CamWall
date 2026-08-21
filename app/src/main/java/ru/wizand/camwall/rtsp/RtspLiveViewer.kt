package ru.wizand.camwall.rtsp

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import java.io.File

/**
 * Просмотр RTSP-потока в реальном времени через долгоживущий сеанс FFmpegKit.
 *
 * План v6, задача A: вместо одного перезаписываемого live.jpg (`-update 1`)
 * FFmpeg пишет последовательность файлов frame_%05d.jpg. Каждый файл
 * появляется в каталоге только после полного закрытия, поэтому читатель
 * никогда не видит недописанный JPEG — моргание (Corrupt JPEG data) исчезает.
 *
 * Low-latency аргументы входа: -fflags nobuffer, -flags low_delay,
 * -probesize 65536, -analyzeduration 0 — быстрый старт без буферизации.
 *
 * Почему не ExoPlayer: media3-exoplayer-rtsp требует fmtp в SDP и падает на
 * дешёвых камерах/NVR (см. RtspFrameCapture). FFmpeg работает со всеми теми же
 * камерами, что и snapshot-режим.
 *
 * Файлы живут в cacheDir (не files); старые кадры чистятся по мере появления
 * новых, stop() чистит весь каталог. Сеанс останавливается обязательно
 * (DisposableEffect экрана) — иначе FFmpeg продолжит держать RTSP-сессию и CPU.
 */
class RtspLiveViewer(context: Context) {

    private val liveDir = File(context.cacheDir, "live").apply { mkdirs() }

    private var session: FFmpegSession? = null

    // Кэш результата latestFrameFile между вызовами (опрос каждые ~100 мс,
    // листать каталог на каждый тик дорого).
    private var cachedLatest: File? = null
    private var cachedFileCount = -1

    val isRunning: Boolean
        get() = session != null

    /**
     * Запускает сеанс захвата. Если сеанс уже идёт — ничего не делает.
     */
    fun start(rtspUrl: String) {
        if (session != null) return

        // Сброс каталога: старые кадры от предыдущих сеансов не нужны.
        liveDir.listFiles()?.forEach { it.delete() }
        cachedLatest = null
        cachedFileCount = -1

        val args = arrayOf(
            "-y",
            "-rtsp_transport", "tcp",
            // Low-latency вход: быстрый старт, минимум буферизации.
            "-fflags", "nobuffer",
            "-flags", "low_delay",
            "-probesize", "65536",
            "-analyzeduration", "0",
            "-i", rtspUrl,
            "-q:v", "3",
            "-vf", "scale='min($MAX_SIDE_PX,iw)':-2",
            // Каждый кадр — отдельный файл; image2 закрывает файл после записи.
            "-f", "image2",
            File(liveDir, "frame_%05d.jpg").absolutePath
        )

        Log.d(TAG, "start: live view session started")
        // executeWithArgumentsAsync: callback по завершении (остановка/ошибка).
        session = FFmpegKit.executeWithArgumentsAsync(args) { completed ->
            Log.d(TAG, "live view session finished: returnCode=${completed.returnCode}")
            session = null
        }
    }

    /**
     * Последний полностью записанный кадр, или null, если кадров ещё нет.
     * image2 пишет файл целиком и закрывает его перед переходом к следующему,
     * поэтому любой существующий frame_*.jpg пригоден для чтения.
     */
    fun latestFrameFile(): File? {
        val files = liveDir.listFiles { f -> f.name.startsWith("frame_") && f.name.endsWith(".jpg") }
            ?: return null
        // Ждём минимум 2 файла: самый новый ещё может дописываться,
        // но раз появился следующий — предыдущий гарантированно закрыт.
        if (files.size < 2) return null

        if (files.size == cachedFileCount && cachedLatest != null) return cachedLatest

        val sorted = files.sortedBy { it.name }
        val safeLatest = sorted[sorted.size - 2] // не самый новый!

        cachedLatest = safeLatest
        cachedFileCount = files.size

        if (files.size > MAX_KEPT_FRAMES) {
            sorted.take(files.size - MAX_KEPT_FRAMES).forEach { it.delete() }
        }
        return safeLatest
    }

    /**
     * Останавливает сеанс и чистит каталог кадров.
     * Идемпотентно: можно звать из DisposableEffect без проверок.
     */
    fun stop() {
        session?.let {
            Log.d(TAG, "stop: cancelling live view session ${it.sessionId}")
            FFmpegKit.cancel(it.sessionId)
        }
        session = null
        liveDir.listFiles()?.forEach { it.delete() }
        cachedLatest = null
        cachedFileCount = -1
    }

    companion object {
        private const val TAG = "RtspLiveViewer"
        private const val MAX_SIDE_PX = 960
        private const val MAX_KEPT_FRAMES = 30
    }
}
