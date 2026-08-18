package ru.wizand.camwall.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.wizand.camwall.domain.model.Camera
import ru.wizand.camwall.domain.usecase.GetCamerasUseCase
import javax.inject.Inject

@HiltViewModel
class CameraWallViewModel @Inject constructor(
    private val getCamerasUseCase: GetCamerasUseCase
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<Camera>>(emptyList())
    val cameras: StateFlow<List<Camera>> = _cameras

    init {
        loadCameras()
    }

    private fun loadCameras() {
        viewModelScope.launch {
            getCamerasUseCase().collect { cameras ->
                _cameras.value = cameras
            }
        }
    }
}