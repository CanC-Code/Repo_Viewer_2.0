package com.explorer.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.explorer.ai.data.NeuralEngineService
import com.explorer.ai.ui.screens.RepoExplorerScreen
import com.explorer.ai.ui.theme.ExplorerAITheme // Assuming standard theme linkage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the low-level hardware ingestion service
        val neuralEngineService = NeuralEngineService(applicationContext)
        
        // Configure standard ViewModel factory to map constructor parameters
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ExplorerViewModel(neuralEngineService) as T
            }
        }
        
        setContent {
            // Material 3 UI Theme Wrapper
            ExplorerAITheme {
                val viewModel: ExplorerViewModel = viewModel(factory = factory)
                
                // Observe the unidirectional state flow from the architecture matrix
                val uiState by viewModel.uiState.collectAsState()
                
                // Inject immutable state downstream to the interactive screen
                RepoExplorerScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}
