package ru.wizand.camwall

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.LinearLayout
import android.util.Log
import ru.wizand.camwall.viewmodels.CameraWallViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val textView = TextView(this).apply {
            text = "CamWall - RTSP Camera Monitor"
        }
        
        layout.addView(textView)
        setContentView(layout)
        
        // Initialize ViewModel without compose
        val app = application as CamWallApplication
        val viewModel = CameraWallViewModel(app.getCamerasUseCase)
    }
}