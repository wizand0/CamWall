package ru.wizand.camwall.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "cameras")
data class Camera(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rtspUrl: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSuccessfulFrameAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val consecutiveErrors: Int = 0,
    val sortOrder: Int = 0
) {
    /**
     * Путь к последнему сохранённому кадру камеры.
     * Кадр хранится в files/cameras/{cameraId}/latest.jpg (ТЗ §8).
     * Room это свойство игнорирует (нет backing field в конструкторе).
     */
    val frameFilePath: String
        get() = "cameras/$id/latest.jpg"

    /**
     * Вычисляемый статус камеры на основе сохранённых данных.
     * Runtime-статус не хранится в Room (ТЗ §17).
     */
    val status: CameraStatus
        get() = when {
            lastError != null -> CameraStatus.ERROR
            lastSuccessfulFrameAt == null -> CameraStatus.NO_DATA
            else -> CameraStatus.ONLINE
        }
}

enum class CameraStatus {
    UNKNOWN,
    UPDATING,
    ONLINE,
    OFFLINE,
    ERROR,
    NO_DATA
}
