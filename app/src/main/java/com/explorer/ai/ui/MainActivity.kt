package com.example.n64manualreader

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    // UI Elements
    private lateinit var etTargetRepositoryURL: EditText
    private lateinit var btnInitializeConnection: Button
    private lateinit var btnFlushAuth: Button
    private lateinit var btnIngestTechnicalManual: Button
    private lateinit var btnSelectPDF: Button
    private lateinit var etLogicQuery: EditText
    private lateinit var btnExecuteQuery: Button
    private lateinit var tvOCRStatus: TextView
    private lateinit var tvChatOutput: TextView
    private lateinit var llChatContainer: LinearLayout

    // OCR
    private lateinit var tessBaseAPI: TessBaseAPI
    private var pdfUri: Uri? = null
    private var knowledgeBase: MutableList<String> = mutableListOf()

    // PDF Picker
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

        // Initialize UI
        initUI()

        // Initialize Tesseract
        initTesseract()

        // Set up button click listeners
        setupButtonListeners()
    }

    private fun initUI() {
        etTargetRepositoryURL = findViewById(R.id.etTargetRepositoryURL)
        btnInitializeConnection = findViewById(R.id.btnInitializeConnection)
        btnFlushAuth = findViewById(R.id.btnFlushAuth)
        btnIngestTechnicalManual = findViewById(R.id.btnIngestTechnicalManual)
        btnSelectPDF = findViewById(R.id.btnSelectPDF)
        etLogicQuery = findViewById(R.id.etLogicQuery)
        btnExecuteQuery = findViewById(R.id.btnExecuteQuery)
        tvOCRStatus = findViewById(R.id.tvOCRStatus)
        tvChatOutput = findViewById(R.id.tvChatOutput)
        llChatContainer = findViewById(R.id.llChatContainer)
    }

    private fun setupButtonListeners() {
        btnInitializeConnection.setOnClickListener {
            val url = etTargetRepositoryURL.text.toString()
            if (url.isNotEmpty()) {
                appendChatMessage("System", "Initializing connection to: $url")
            } else {
                Toast.makeText(this, "Please enter a repository URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnFlushAuth.setOnClickListener {
            appendChatMessage("System", "Flushing authentication...")
        }

        btnIngestTechnicalManual.setOnClickListener {
            // Open PDF picker
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pdfPickerResult.launch(intent)
        }

        btnSelectPDF.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pdfPickerResult.launch(intent)
        }

        btnExecuteQuery.setOnClickListener {
            val query = etLogicQuery.text.toString()
            if (query.isNotEmpty()) {
                processQuery(query)
            } else {
                Toast.makeText(this, "Please enter a query", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initTesseract() {
        val tessDataDir = File(filesDir, "tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }

        // Copy trained data (e.g., eng.traineddata) to tessdata folder
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
                updateOCRStatus("Processing PDF...")
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfDocument = PDDocument.load(inputStream)
                    val pdfRenderer = PDFRenderer(pdfDocument)

                    // Process each page
                    for (pageIndex in 0 until pdfDocument.numberOfPages) {
                        val bitmap = pdfRenderer.renderImageWithDPI(pageIndex, 300f)
                        val text = extractTextFromBitmap(bitmap)
                        knowledgeBase.add(text)
                        appendChatMessage("System", "Extracted text from page ${pageIndex + 1}")
                    }

                    pdfDocument.close()
                    updateOCRStatus("PDF processed successfully! ${knowledgeBase.size} pages extracted.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "PDF processed successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                updateOCRStatus("Error: ${e.message}")
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

    private fun processQuery(query: String) {
        // Simple keyword matching for demo purposes
        val results = knowledgeBase.filter { it.contains(query, ignoreCase = true) }
        if (results.isNotEmpty()) {
            appendChatMessage("AI", "Found ${results.size} matches for: \"$query\"")
            results.forEachIndexed { index, text ->
                appendChatMessage("AI", "Match ${index + 1}: ${text.take(100)}...")
            }
        } else {
            appendChatMessage("AI", "No matches found for: \"$query\"")
        }
    }

    private fun appendChatMessage(sender: String, message: String) {
        runOnUiThread {
            val chatMessage = "$sender: $message"
            tvChatOutput.text = "${tvChatOutput.text}\n$chatMessage"
            // Scroll to bottom
            llChatContainer.post {
                llChatContainer.scrollTo(0, llChatContainer.bottom)
            }
        }
    }

    private fun updateOCRStatus(status: String) {
        runOnUiThread {
            tvOCRStatus.text = "OCR Status: $status"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tessBaseAPI.end()
    }
}