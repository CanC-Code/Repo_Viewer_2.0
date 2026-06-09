package com.explorer.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.explorer.ai.data.NeuralEngineService
import com.explorer.ai.data.PdfProcessor
import com.explorer.ai.ui.screens.RepoExplorerScreen

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL: Initialize PDFBox framework against the app context at boot
        // Prevents unhandled exceptions during spatial document extraction
        PdfProcessor.init(applicationContext)
        
        // Initialize the low-level spatial and hardware ingestion service
        val neuralEngineService = NeuralEngineService(applicationContext)
        
        // Configure standard ViewModel factory to map constructor parameters
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ExplorerViewModel(neuralEngineService) as T
            }
        }
        
        // Instantiate ViewModel natively via the standard ComponentActivity provider
        val viewModel = ViewModelProvider(this, factory)[ExplorerViewModel::class.java]
        
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    
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
}
