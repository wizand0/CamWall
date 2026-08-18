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
)

enum class CameraStatus {
    UNKNOWN,
    UPDATING,
    ONLINE,
    OFFLINE,
    ERROR,
    NO_DATA
}