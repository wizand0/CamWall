package ru.wizand.camwall.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.util.Converters

@Database(
    entities = [Camera::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    companion object {
        const val DATABASE_NAME = "camwall_database"
    }
}