package ru.wizand.camwall.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.security.RtspUrlCryptoStore
import ru.wizand.camwall.util.Converters

@Database(
    entities = [Camera::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    companion object {
        private var INSTANCE: AppDatabase? = null
        const val DATABASE_NAME = "camwall_database"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(migration1To2(context))
                .build()
        }

        /**
         * Миграция 1 -> 2 (этап 2, безопасность): RTSP-URL убирается из Room.
         *
         * 1. Все plain-text URL вычитываются из таблицы и перекладываются в
         *    EncryptedSharedPreferences (RtspUrlCryptoStore), ключ — id камеры.
         * 2. Колонка rtspUrl удаляется из схемы.
         *
         * Миграция одноразовая и идемпотентная: если в шифрованном хранилище
         * URL для камеры уже есть, он просто перезаписывается тем же значением.
         */
        private fun migration1To2(context: Context): Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val store = RtspUrlCryptoStore(context)
                db.query("SELECT id, rtspUrl FROM cameras").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val urlIdx = cursor.getColumnIndex("rtspUrl")
                    if (idIdx >= 0 && urlIdx >= 0) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idIdx)
                            val url = cursor.getString(urlIdx)
                            if (!id.isNullOrBlank() && !url.isNullOrBlank()) {
                                store.storeUrl(id, url)
                            }
                        }
                    }
                }
                db.execSQL("ALTER TABLE cameras DROP COLUMN rtspUrl")
            }
        }
    }
}
