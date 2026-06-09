package com.explorer.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfProcessor {
    
    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    fun extractTextAndAssetsFromUri(context: Context, uri: Uri): String {
        var document: PDDocument? = null
        val extractedContent = java.lang.StringBuilder()
        
        // Establish a clean directory for extracted images and diagrams
        val assetsDir = File(context.filesDir, "extracted_pdf_assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)
                
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true 
                    suppressDuplicateOverlappingText = true
                }

                val numPages = document.numberOfPages
                for (page in 1..numPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    
                    // 1. Extract and sanitize layout text
                    val rawPageText = stripper.getText(document) ?: ""
                    val cleanedText = cleanExtractedText(rawPageText)
                    
                    if (cleanedText.isNotBlank() || hasVisualElements(document, page)) {
                        extractedContent.append("--- START OF PAGE ").append(page).append(" ---\n")
                        
                        if (cleanedText.isNotBlank()) {
                            extractedContent.append(cleanedText).append("\n")
                        }
                        
                        // 2. Extract embedded schematics, diagrams, or tables from this page
                        // Removed the unused Context parameter here
                        val visualTokens = extractPageImages(document, page, assetsDir)
                        if (visualTokens.isNotEmpty()) {
                            extractedContent.append("\n").append(visualTokens).append("\n")
                        }
                        
                        extractedContent.append("--- END OF PAGE ").append(page).append(" ---\n\n")
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("PdfProcessor", "Local system memory limit reached during deep extraction: ${e.message}")
            extractedContent.append("\nSYSTEM_NOTE: Memory threshold breached. Partial visual context mapped.")
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Processing fault encountered: ${e.message}")
        } finally {
            document?.close()
        }

        return extractedContent.toString()
    }

    private fun cleanExtractedText(rawText: String): String {
        val lines = rawText.lines()
        val cleanedLines = mutableListOf<String>()
        val tocRegex = Regex("[\\.]{4,}")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (tocRegex.containsMatchIn(trimmed)) continue
            
            val sanitizedLine = trimmed.replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF\\n\\r\\t]"), "")
            if (sanitizedLine.isNotBlank()) {
                cleanedLines.add(sanitizedLine)
            }
        }
        return cleanedLines.joinToString("\n")
    }

    private fun hasVisualElements(document: PDDocument, pageNum: Int): Boolean {
        return try {
            val page = document.getPage(pageNum - 1)
            val resources = page.resources
            resources?.xObjectNames?.any { resources.getXObject(it) is PDImageXObject } ?: false
        } catch (e: Exception) {
            false
        }
    }

    // Unused Context parameter removed from method signature
    private fun extractPageImages(document: PDDocument, pageNum: Int, assetsDir: File): String {
        val tokenBuilder = java.lang.StringBuilder()
        try {
            val page = document.getPage(pageNum - 1)
            val resources = page.resources ?: return ""
            var imageCounter = 1

            for (name in resources.xObjectNames) {
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    val bitmap = xObject.image
                    if (bitmap != null) {
                        val assetFile = File(assetsDir, "doc_page_${pageNum}_visual_${imageCounter}.png")
                        FileOutputStream(assetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        
                        // Inject explicit tracking anchors into the context stream
                        tokenBuilder.append("[DIAGRAM_REFERENCE: FilePath=\"")
                                    .append(assetFile.absolutePath)
                                    .append("\"; Context=\"Embedded hardware layout/schematic on Page ")
                                    .append(pageNum)
                                    .append("\"]\n")
                        imageCounter++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Failed extracting visual assets from page $pageNum: ${e.message}")
        }
        return tokenBuilder.toString()
    }
    
    fun chunkText(text: String, chunkSize: Int = 800, overlap: Int = 150): List<String> {
        if (text.startsWith("SYSTEM_NOTE")) return listOf(text)
        
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<String>()
        var i = 0
        
        while (i < words.size) {
            val end = minOf(i + chunkSize, words.size)
            chunks.add(words.subList(i, end).joinToString(" "))
            i += (chunkSize - overlap)
        }
        
        return chunks.ifEmpty { listOf("SYSTEM_NOTE: Asset compilation chunking exception.") }
    }
}
