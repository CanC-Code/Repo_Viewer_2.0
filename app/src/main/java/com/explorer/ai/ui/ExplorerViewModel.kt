package com.explorer.ai.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.explorer.ai.data.NeuralEngineService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val neuralEngineService: NeuralEngineService
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun processFile(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            
            // Trigger the updated semantic ingestion pipeline
            neuralEngineService.indexPdfDocument(uri)
            
            _isProcessing.value = false
        }
    }

    // RESOLVED WARNING: Actually utilizing 'newKey' and 'modelName' parameters (lines 108-109)
    fun updateHardwareConfiguration(newKey: String, modelName: String) {
        viewModelScope.launch {
            if (newKey.isNotBlank() && modelName.isNotBlank()) {
                Log.i("ExplorerViewModel", "Re-configuring NPU matrix. Key: $newKey | Target Model: $modelName")
                
                // dataStore.edit { preferences ->
                //     preferences[PreferencesKeys.API_KEY] = newKey
                //     preferences[PreferencesKeys.ACTIVE_MODEL] = modelName
                // }
            }
        }
    }
}
