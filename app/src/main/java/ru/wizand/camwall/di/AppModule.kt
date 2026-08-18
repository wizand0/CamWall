package ru.wizand.camwall.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.wizand.camwall.data.local.database.AppDatabase
import ru.wizand.camwall.data.local.database.CameraDao
import ru.wizand.camwall.data.repository.CameraRepositoryImpl
import ru.wizand.camwall.domain.repository.ICameraRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideCameraDao(database: AppDatabase): CameraDao {
        return database.cameraDao()
    }

    @Provides
    @Singleton
    fun provideCameraRepository(repository: CameraRepositoryImpl): ICameraRepository {
        return repository
    }
}