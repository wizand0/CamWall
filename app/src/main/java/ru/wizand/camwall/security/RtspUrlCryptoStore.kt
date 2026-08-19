package ru.wizand.camwall.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Зашифрованное хранилище RTSP-URL (этап 2, безопасность).
 *
 * Полные URL с логинами/паролями НЕ хранятся в Room, DataStore и логах —
 * только здесь, в EncryptedSharedPreferences:
 * - ключи и значения шифруются AES-256 (SIV для ключей, GCM для значений);
 * - мастер-ключ живёт в Android Keystore (не извлекается из secure-зоны).
 *
 * Ключ записи — id камеры, значение — полный RTSP-URL.
 */
class RtspUrlCryptoStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeUrl(cameraId: String, url: String) {
        prefs.edit().putString(urlKey(cameraId), url).apply()
    }

    fun getUrl(cameraId: String): String? =
        prefs.getString(urlKey(cameraId), null)

    fun removeUrl(cameraId: String) {
        prefs.edit().remove(urlKey(cameraId)).apply()
    }

    private fun urlKey(cameraId: String): String = KEY_PREFIX + cameraId

    companion object {
        private const val PREFS_FILE_NAME = "rtsp_secrets"
        private const val KEY_PREFIX = "rtsp_url_"
    }
}
