package com.explorer.ai

import android.app.Application
import com.tomroush.pdfbox.android.PDFBoxResourceLoader

class ExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the local PDF engine asset cache safely on boot
        PDFBoxResourceLoader.init(applicationContext)
    }
}
