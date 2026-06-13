package com.explorer.ai.domain

import android.util.Log
import com.explorer.ai.ui.LocalLlmEngine

class NativeHardwareLlmEngine(private val modelPath: String) : LocalLlmEngine {

    init {
        try {
            // Load the compiled C++ library defined in your CMakeLists.txt
            System.loadLibrary("hardware_inference_engine")
            
            // Execute the native setup routine immediately upon instantiation
            val isInitialized = initNativeEngine(modelPath)
            if (!isInitialized) {
                Log.e("NativeHardwareEngine", "Native initialization returned false. Check hardware support.")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeHardwareEngine", "Failed to load C++ JNI library: ${e.message}")
        }
    }

    private external fun initNativeEngine(modelPath: String): Boolean
    private external fun processPromptNative(prompt: String): String

    override suspend fun generateResponse(prompt: String): String {
        return try {
            processPromptNative(prompt)
        } catch (e: Exception) {
            "SYSTEM_FAULT: JNI Bridge Execution Failed - ${e.localizedMessage}"
        }
    }
}
