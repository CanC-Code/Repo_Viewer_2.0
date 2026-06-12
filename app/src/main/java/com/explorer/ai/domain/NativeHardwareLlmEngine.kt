package com.explorer.ai.domain

import android.util.Log
import com.explorer.ai.ui.LocalLlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeHardwareLlmEngine(private val localModelPath: String) : LocalLlmEngine {

    init {
        try {
            // Load the generated CMake library
            System.loadLibrary("hardware_inference_engine")
            val isInitialized = initNativeEngine(localModelPath)
            if (!isInitialized) {
                Log.e("NativeLlmEngine", "NNAPI hardware mapping failed during initialization.")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeLlmEngine", "Failed to load the native C++ hardware bridge: ${e.message}")
        }
    }

    // JNI External bindings mapped to hardware_inference_engine.cpp
    private external fun initNativeEngine(modelPath: String): Boolean
    private external fun processPromptNative(prompt: String): String

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            // Pipe the multi-modal prompt into the C++ native layer
            val hardwareResponse = processPromptNative(prompt)
            
            if (hardwareResponse.isBlank()) {
                "SYSTEM_NOTE: Native hardware NPU returned an empty tensor."
            } else {
                hardwareResponse
            }
        } catch (e: Exception) {
            "SYSTEM_NOTE: Native execution fault - ${e.localizedMessage}"
        }
    }
}