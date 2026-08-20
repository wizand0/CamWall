package ru.wizand.camwall

import android.app.Application
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.Level
import ru.wizand.camwall.util.RtspUrlMasker

class CamWallApplication : Application() {
    companion object {
        lateinit var instance: CamWallApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupFfmpegLogging()
    }

    /**
     * План v6, задача B: ffmpeg-kit по умолчанию печатает нативные логи в logcat,
     * включая аргументы команды — туда попадает RTSP URL с паролем
     * ("Input #0, rtsp, from 'rtsp://user:password@...'").
     *
     * В используемом форке (com.moizhassan.ffmpeg:ffmpeg-kit-16kb) нет
     * enableRedactedLog(), поэтому перехватываем логи callback'ом и маскируем
     * URL. При определённом callback ffmpeg-kit сам перестаёт печатать логи
     * (стратегия по умолчанию PRINT_LOGS_WHEN_NO_CALLBACKS_DEFINED).
     */
    private fun setupFfmpegLogging() {
        FFmpegKitConfig.enableLogCallback { log ->
            val message = log.message ?: return@enableLogCallback
            Log.println(log.level.toAndroidPriority(), "ffmpeg-kit", RtspUrlMasker.maskAll(message))
        }
    }

    private fun Level.toAndroidPriority(): Int = when (this) {
        Level.AV_LOG_PANIC, Level.AV_LOG_FATAL, Level.AV_LOG_ERROR -> Log.ERROR
        Level.AV_LOG_WARNING -> Log.WARN
        Level.AV_LOG_INFO -> Log.INFO
        else -> Log.DEBUG
    }
}
