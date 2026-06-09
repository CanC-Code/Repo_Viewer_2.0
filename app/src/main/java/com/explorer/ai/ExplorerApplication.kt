package com.explorer.ai

import android.app.Application

class ExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Legacy PDFBoxResourceLoader.init() removed.
        // Optical ML Kit matrices will initialize dynamically upon first document ingestion.
    }
}
