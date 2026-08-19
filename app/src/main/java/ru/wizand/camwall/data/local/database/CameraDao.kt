package ru.wizand.camwall.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.wizand.camwall.domain.model.Camera

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY sortOrder ASC, name ASC")
    fun getAllCameras(): Flow<List<Camera>>

    @Query("SELECT * FROM cameras WHERE id = :id")
    fun getCameraById(id: String): Flow<Camera?>

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun getCameraByIdSync(id: String): Camera?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: Camera)

    @Update
    suspend fun updateCamera(camera: Camera)

    @Delete
    suspend fun deleteCamera(camera: Camera)

    @Query("DELETE FROM cameras WHERE id = :id")
    suspend fun deleteCameraById(id: String)

    @Query("UPDATE cameras SET lastSuccessfulFrameAt = :frameTimestamp, lastAttemptAt = :attemptTimestamp, lastError = :error, consecutiveErrors = CASE WHEN :error IS NULL THEN 0 ELSE consecutiveErrors + 1 END WHERE id = :cameraId")
    suspend fun updateCameraFrame(
        cameraId: String,
        frameTimestamp: Long?,
        attemptTimestamp: Long = System.currentTimeMillis(),
        error: String? = null
    )
}