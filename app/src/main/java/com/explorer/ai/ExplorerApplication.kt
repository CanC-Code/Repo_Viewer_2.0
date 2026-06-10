package com.explorer.ai

import android.app.Application
import android.util.Log

/**
 * Application class. Registered in AndroidManifest.xml via android:name=".ExplorerApplication".
 * Previously this was missing from the manifest, so it never ran.
 */
class ExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("ExplorerApp", "Application initialised.")
        // ML Kit text recognizer initialises lazily on first use — no explicit init needed.
        // Future: pre-warm the ML Kit model here if cold-start latency is a concern.
    }
}
