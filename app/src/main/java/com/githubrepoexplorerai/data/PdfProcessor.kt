package com.githubrepoexplorerai.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

object PdfProcessor {
    
    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    fun extractTextFromUri(context: Context, uri: Uri): String {
        var document: PDDocument? = null
        var text = ""
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)
                
                // CRITICAL FIX FOR N64 MANUAL: 
                // sortByPosition forces PDFBox to read complex, older, or multi-column layouts correctly.
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true 
                    suppressDuplicateOverlappingText = true
                }
                text = stripper.getText(document) ?: ""
            }
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Error parsing PDF: ${e.message}")
        } finally {
            document?.close()
        }

        return cleanExtractedText(text)
    }

    private fun cleanExtractedText(rawText: String): String {
        if (rawText.isBlank()) {
            Log.w("PdfProcessor", "Warning: Extracted text is empty. Document may be scanned/image-based.")
            return "SYSTEM_NOTE: This document appears to be empty or requires OCR. No readable text was extracted."
        }

        val lines = rawText.lines()
        val cleanedLines = mutableListOf<String>()
        
        // Regex to detect and eliminate Table of Contents leader lines (e.g., "....... 10")
        val tocRegex = Regex("[\\.]{4,}")
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Skip empty lines or lines that match TOC patterns
            if (trimmed.isEmpty()) continue
            if (tocRegex.containsMatchIn(trimmed)) continue
            
            // Strip out non-standard artifacts but preserve standard alphanumeric and punctuation
            val sanitizedLine = trimmed.replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF\\n\\r]"), "")
            if (sanitizedLine.isNotBlank()) {
                cleanedLines.add(sanitizedLine)
            }
        }
        return cleanedLines.joinToString("\n")
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
        
        return if (chunks.isEmpty()) listOf("SYSTEM_NOTE: Chunking failed, text unreadable.") else chunks
    }
}
