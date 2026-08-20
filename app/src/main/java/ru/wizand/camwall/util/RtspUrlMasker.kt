package ru.wizand.camwall.util

/**
 * Маскировка RTSP URL для логов и UI (ТЗ §43):
 * rtsp://user:password@host:554/path -> rtsp://user:****@host:554/path
 */
object RtspUrlMasker {

    private val URL_WITH_CREDENTIALS = Regex("^(rtsp://)([^@/]+)@(.*)$", RegexOption.IGNORE_CASE)

    // RTSP URL с credentials внутри произвольного текста (логи ffmpeg-kit и т.п.).
    private val EMBEDDED_URL = Regex("rtsp://([^@/\\s]+)@", RegexOption.IGNORE_CASE)

    fun mask(url: String): String {
        val match = URL_WITH_CREDENTIALS.find(url.trim()) ?: return url
        val userInfo = match.groupValues[2]
        val user = userInfo.substringBefore(':')
        return "${match.groupValues[1]}$user:****@${match.groupValues[3]}"
    }

    /**
     * Маскирует ВСЕ rtsp://user:password@ вхождения в произвольном тексте
     * (например, в строке лога ffmpeg-kit). Пароли не должны попадать в logcat.
     */
    fun maskAll(text: String): String = EMBEDDED_URL.replace(text) { m ->
        val userInfo = m.groupValues[1]
        val user = userInfo.substringBefore(':')
        "rtsp://$user:****@"
    }
}
