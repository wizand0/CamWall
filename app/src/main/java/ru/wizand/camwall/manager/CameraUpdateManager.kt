package ru.wizand.camwall.manager

import android.content.Context
import androidx.work.*
import ru.wizand.camwall.worker.CameraUpdateWorker
import java.util.concurrent.TimeUnit

class CameraUpdateManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    
    fun scheduleCameraUpdates(intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val updateWorkRequest = PeriodicWorkRequestBuilder<CameraUpdateWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            CameraUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            updateWorkRequest
        )
    }
    
    fun cancelCameraUpdates() {
        workManager.cancelUniqueWork(CameraUpdateWorker.WORK_NAME)
    }
    
    fun triggerOneTimeUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val updateWorkRequest = OneTimeWorkRequestBuilder<CameraUpdateWorker>()
            .setConstraints(constraints)
            .build()
        
        workManager.enqueue(updateWorkRequest)
    }
}