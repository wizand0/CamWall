package ru.wizand.camwall.viewmodel_factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.wizand.camwall.data.repository.CameraRepositoryImpl
import ru.wizand.camwall.data.local.database.AppDatabase
import ru.wizand.camwall.domain.usecase.*
import ru.wizand.camwall.rtsp.RtspFrameCapture
import ru.wizand.camwall.security.RtspUrlCryptoStore
import ru.wizand.camwall.viewmodels.CameraWallViewModel

class CameraWallViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraWallViewModel::class.java)) {
            val database = AppDatabase.getInstance(application)
            val cameraDao = database.cameraDao()
            val cryptoStore = RtspUrlCryptoStore(application)
            val cameraRepository = CameraRepositoryImpl(cameraDao, cryptoStore)
            
            val getCamerasUseCase = GetCamerasUseCase(cameraRepository)
            val addCameraUseCase = AddCameraUseCase(cameraRepository)
            val deleteCameraUseCase = DeleteCameraUseCase(cameraRepository)
            val updateCameraUseCase = UpdateCameraUseCase(cameraRepository)
            val rtspFrameCapture = RtspFrameCapture(application)
            
            @Suppress("UNCHECKED_CAST")
            return CameraWallViewModel(
                application,
                getCamerasUseCase,
                addCameraUseCase,
                deleteCameraUseCase,
                updateCameraUseCase,
                cameraRepository,
                rtspFrameCapture
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}