package com.explorer.ai.data

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
    
    fun extractTextAndAssetsFromUri(context: android.content.Context, uri: Uri): String {
        var document: PDDocument? = null
        val extractedContent = java.lang.StringBuilder()
        val assetsDir = File(context.filesDir, "extracted_pdf_assets").apply { if (!exists()) mkdirs() }
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val numPages = document.numberOfPages

                for (page in 1..numPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    
                    val rawPageText = stripper.getText(document) ?: ""
                    val cleanedText = cleanExtractedText(rawPageText)
                    
                    // Extract section context (first 50 chars of page) to label the diagram
                    val pageHeading = cleanedText.take(50).replace("\n", " ").trim()
                    
                    if (cleanedText.isNotBlank() || hasVisualElements(document, page)) {
                        extractedContent.append("--- PAGE ").append(page).append(" ---\n")
                        extractedContent.append(cleanedText).append("\n")
                        
                        // Pass pageHeading into visual extraction to provide context for the image
                        val visualTokens = extractPageImages(document, page, assetsDir, pageHeading)
                        if (visualTokens.isNotEmpty()) {
                            extractedContent.append("\n").append(visualTokens).append("\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Extraction error: ${e.message}")
        } finally {
            document?.close()
        }
        return extractedContent.toString()
    }

    private fun extractPageImages(document: PDDocument, pageNum: Int, assetsDir: File, contextLabel: String): String {
        val tokenBuilder = java.lang.StringBuilder()
        try {
            val page = document.getPage(pageNum - 1)
            val resources = page.resources ?: return ""
            var imageCounter = 1

            for (name in resources.xObjectNames) {
                val xObject = resources.getXObject(name)
                if (xObject is PDImageXObject) {
                    val bitmap = xObject.image
                    val assetFile = File(assetsDir, "page_${pageNum}_img_${imageCounter}.png")
                    
                    // Save image
                    FileOutputStream(assetFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 80, out) }
                    
                    // Metadata injection: This helps the LLM "learn" what the diagram is about
                    tokenBuilder.append("[DIAGRAM_REFERENCE: File=\"").append(assetFile.name)
                                .append("\"; Page=").append(pageNum)
                                .append("; SemanticContext=\"").append(contextLabel).append("\"]\n")
                    imageCounter++
                }
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Visual extraction failed: ${e.message}")
        }
        return tokenBuilder.toString()
    }

    private fun cleanExtractedText(raw: String) = raw.replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF\\n\\r\\t]"), "").trim()
    private fun hasVisualElements(doc: PDDocument, pageNum: Int) = doc.getPage(pageNum - 1).resources?.xObjectNames?.any { doc.getPage(pageNum - 1).resources.getXObject(it) is PDImageXObject } ?: false
}
