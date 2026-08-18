package ru.wizand.camwall

import android.app.Application
import androidx.room.Room
import ru.wizand.camwall.data.local.database.AppDatabase
import ru.wizand.camwall.data.repository.CameraRepositoryImpl
import ru.wizand.camwall.domain.repository.ICameraRepository
import ru.wizand.camwall.domain.usecase.AddCameraUseCase
import ru.wizand.camwall.domain.usecase.DeleteCameraUseCase
import ru.wizand.camwall.domain.usecase.GetCamerasUseCase
import ru.wizand.camwall.domain.usecase.UpdateCameraUseCase

class CamWallApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var cameraRepository: ICameraRepository
    
    // Use cases
    lateinit var getCamerasUseCase: GetCamerasUseCase
    lateinit var addCameraUseCase: AddCameraUseCase
    lateinit var updateCameraUseCase: UpdateCameraUseCase
    lateinit var deleteCameraUseCase: DeleteCameraUseCase
    
    companion object {
        lateinit var instance: CamWallApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
        
        // Initialize repository
        cameraRepository = CameraRepositoryImpl(database.cameraDao())
        
        // Initialize use cases
        getCamerasUseCase = GetCamerasUseCase(cameraRepository)
        addCameraUseCase = AddCameraUseCase(cameraRepository)
        updateCameraUseCase = UpdateCameraUseCase(cameraRepository)
        deleteCameraUseCase = DeleteCameraUseCase(cameraRepository)
    }
}