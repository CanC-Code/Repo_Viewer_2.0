package com.explorer.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

object PdfProcessor {

    suspend fun visuallyReadAndExtractUri(context: Context, uri: Uri): String {
        val extractedContent = StringBuilder()
        val assetsDir = File(context.filesDir, "extracted_pdf_assets").apply { if (!exists()) mkdirs() }
        
        try {
            // Use standard Android content resolution to map the file securely
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fileDescriptor ->
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                val numPages = pdfRenderer.pageCount
                for (pageIndex in 0 until numPages) {
                    val page = pdfRenderer.openPage(pageIndex)
                    
                    // Render the page to a high-res Bitmap (Fixed at 1440px wide for optimal OCR balance and strict memory safety)
                    val scale = 1440f / page.width
                    val width = 1440
                    val height = (page.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE) // PDF backgrounds are transparent by default; ML Kit requires high-contrast white
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    extractedContent.append("\n--- PAGE_START: ").append(pageIndex + 1).append(" ---\n")
                    
                    // 1. Visually analyze the physical layout using ML Kit's Computer Vision
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    
                    // 2. Map structural boundaries (Paragraphs, Spacing, Columns) directly from the visual output
                    if (result.textBlocks.isEmpty()) {
                        extractedContent.append("[VISUAL_NOTE: Computer vision detected no readable structural text blocks]\n")
                    } else {
                        for (block in result.textBlocks) {
                            extractedContent.append("[PARAGRAPH_START: Physical_Bounds=").append(block.boundingBox?.toShortString()).append("]\n")
                            extractedContent.append(block.text.replace("\n", " ")).append("\n")
                            extractedContent.append("[PARAGRAPH_END]\n\n")
                        }
                    }
                    
                    // 3. Diagram & Schematic anchoring
                    // If the page contains very few text blocks but rendered successfully, tag it as an original hardware diagram
                    if (result.textBlocks.size < 4 && pageIndex > 0) {
                        val assetFile = File(assetsDir, "visual_map_p${pageIndex + 1}.png")
                        FileOutputStream(assetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
                        }
                        val spatialContext = result.text.take(100).replace("\n", " ").ifBlank { "Embedded Visual Hardware Graphic" }
                        extractedContent.append("[VISUAL_ANCHOR: File=\"").append(assetFile.name)
                                        .append("\"; SpatialContext=\"").append(spatialContext).append("\"]\n")
                    }
                    
                    // Critical memory cleanup to prevent OOM across 500+ pages
                    page.close()
                    bitmap.recycle()
                    extractedContent.append("--- PAGE_END: ").append(pageIndex + 1).append(" ---\n")
                }
                pdfRenderer.close()
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Visual mapping fault: ${e.message}")
            extractedContent.append("\n[SYSTEM_FAULT: Optical Matrix Processing Failed - ${e.localizedMessage}]")
        }

        return extractedContent.toString()
    }

    fun chunkTextSemantically(text: String, maxChunkSize: Int = 1200): List<String> {
        if (text.startsWith("[SYSTEM_FAULT")) return listOf(text)
        
        val blocks = text.split(Regex("(?=\\[PARAGRAPH_START|--- PAGE_START)"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        
        for (block in blocks) {
            if (currentChunk.length + block.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(block).append("\n")
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        return chunks.ifEmpty { listOf("[SYSTEM_FAULT: Context vectorization failed.]") }
    }
}
