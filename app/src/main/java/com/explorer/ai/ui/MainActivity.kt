package com.explorer.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.explorer.ai.data.NeuralEngineService
import com.explorer.ai.domain.NativeHardwareLlmEngine
import com.explorer.ai.ui.screens.RepoExplorerScreen
import java.io.File

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the low-level spatial and hardware ingestion service
        val neuralEngineService = NeuralEngineService(applicationContext)
        
        // Define path for the quantized model weights (e.g., Qwen/SmolLM loaded to internal storage)
        val modelPath = File(applicationContext.filesDir, "model.bin").absolutePath
        val nativeHardwareEngine = NativeHardwareLlmEngine(modelPath)
        
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ExplorerViewModel(neuralEngineService, nativeHardwareEngine) as T
            }
        }
        
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    
                    // Native Compose injection - safe from main thread crashes
                    val viewModel: ExplorerViewModel = viewModel(factory = factory)
                    val uiState by viewModel.uiState.collectAsState()
                    
                    RepoExplorerScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
