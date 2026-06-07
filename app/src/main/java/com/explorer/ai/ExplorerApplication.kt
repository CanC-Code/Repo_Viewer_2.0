package com.explorer.ai

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
