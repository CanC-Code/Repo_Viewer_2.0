package com.example.n64manualreader

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tessBaseAPI: TessBaseAPI
    private var pdfUri: Uri? = null
    private val pdfPickerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pdfUri = uri
                processPDF(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Tesseract
        initTesseract()

        // Open PDF Picker
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pdfPickerResult.launch(intent)
    }

    private fun initTesseract() {
        val tessDataDir = File(filesDir, "tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }

        // Copy trained data (e.g., eng.traineddata) to tessdata folder
        // You need to include this file in assets/tessdata/
        val tessDataFile = File(tessDataDir, "eng.traineddata")
        if (!tessDataFile.exists()) {
            assets.open("tessdata/eng.traineddata").use { input ->
                FileOutputStream(tessDataFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        tessBaseAPI = TessBaseAPI().apply {
            setDebug(true)
            init(filesDir.absolutePath, "eng")
        }
    }

    private fun processPDF(uri: Uri) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)
                    val pdfRenderer = PDFRenderer(pdfDocument)

                    // Process each page
                    for (pageIndex in 0 until pdfDocument.numberOfPages) {
                        val bitmap = pdfRenderer.renderImageWithDPI(pageIndex, 300f)
                        val text = extractTextFromBitmap(bitmap)
                        analyzeText(text, pageIndex + 1)
                    }

                    pdfDocument.close()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "PDF processed successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun extractTextFromBitmap(bitmap: Bitmap): String {
        tessBaseAPI.setImage(bitmap)
        return tessBaseAPI.utF8Text
    }

    private fun analyzeText(text: String, pageNumber: Int) {
        // Implement your text analysis logic here
        // Example: Store extracted text in a database or process queries
        println("Page $pageNumber: $text")
    }

    override fun onDestroy() {
        super.onDestroy()
        tessBaseAPI.end()
    }
}