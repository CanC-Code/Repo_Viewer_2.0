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
        val extractedContent = StringBuilder()
        val assetsDir = File(context.filesDir, "extracted_pdf_assets").apply { if (!exists()) mkdirs() }
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)
                
                // Configure Stripper for Spatial & Layout Awareness
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    suppressDuplicateOverlappingText = true
                    // Inject explicit tokens to preserve document geometry
                    paragraphStart = "[PARAGRAPH_START]\n"
                    paragraphEnd = "\n[PARAGRAPH_END]\n"
                    articleStart = "[COLUMN_START]\n"
                    articleEnd = "\n[COLUMN_END]\n"
                }

                val numPages = document.numberOfPages
                for (page in 1..numPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    
                    val rawPageText = stripper.getText(document) ?: ""
                    val structuredText = cleanAndStructureText(rawPageText)
                    
                    if (structuredText.isNotBlank() || hasVisualElements(document, page)) {
                        extractedContent.append("\n--- PAGE_START: ").append(page).append(" ---\n")
                        
                        // Pass the structured text block so the diagram engine can anchor to the nearest context
                        val visualTokens = extractPageImagesAndAnchor(document, page, assetsDir, structuredText)
                        
                        // Interleave the text and the anchored visual metadata
                        extractedContent.append(structuredText)
                        if (visualTokens.isNotEmpty()) {
                            extractedContent.append("\n").append(visualTokens).append("\n")
                        }
                        
                        extractedContent.append("--- PAGE_END: ").append(page).append(" ---\n")
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("PdfProcessor", "Memory threshold breached during spatial mapping: ${e.message}")
            extractedContent.append("\n[SYSTEM_FAULT: Temporal mapping truncated due to local memory limits.]")
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Extraction error: ${e.message}")
        } finally {
            document?.close()
        }

        return extractedContent.toString()
    }

    private fun cleanAndStructureText(rawText: String): String {
        // Remove excessive empty lines but preserve our injected spatial tokens
        val lines = rawText.lines()
        val cleaned = mutableListOf<String>()
        val tocRegex = Regex("[\\.]{4,}") // Destroy Table of Contents leaders
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (tocRegex.containsMatchIn(trimmed)) continue
            
            // Allow alphanumeric, punctuation, and our layout brackets []
            val sanitized = trimmed.replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF\\[\\]\\n\\r\\t]"), "")
            if (sanitized.isNotBlank()) {
                cleaned.add(sanitized)
            }
        }
        return cleaned.joinToString("\n")
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

    private fun extractPageImagesAndAnchor(document: PDDocument, pageNum: Int, assetsDir: File, structuredPageText: String): String {
        val tokenBuilder = StringBuilder()
        try {
            val page = document.getPage(pageNum - 1)
            val resources = page.resources ?: return ""
            var imageCounter = 1

            // Attempt to grab a dense paragraph from the page to serve as the semantic anchor
            // This prevents diagrams from being contextually orphaned.
            val paragraphs = structuredPageText.split("[PARAGRAPH_START]")
            val denseAnchor = paragraphs.maxByOrNull { it.length }?.take(100)?.replace("\n", " ") ?: "Unspecified Layout"

            for (name in resources.xObjectNames) {
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    val bitmap = xObject.image
                    if (bitmap != null) {
                        val assetFile = File(assetsDir, "sys_map_p${pageNum}_v${imageCounter}.png")
                        FileOutputStream(assetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        
                        tokenBuilder.append("[VISUAL_ANCHOR: File=\"").append(assetFile.name)
                                    .append("\"; SpatialContext=\"").append(denseAnchor.trim()).append("\"]\n")
                        imageCounter++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Visual mapping failed on page $pageNum: ${e.message}")
        }
        return tokenBuilder.toString()
    }

    // Semantic Chunker: Slices by paragraphs instead of arbitrary word counts
    fun chunkTextSemantically(text: String, maxChunkSize: Int = 1000): List<String> {
        if (text.startsWith("[SYSTEM_FAULT")) return listOf(text)
        
        // Split precisely on our injected structural boundaries
        val blocks = text.split(Regex("(?=\\[PARAGRAPH_START\\]|--- PAGE_START)"))
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
