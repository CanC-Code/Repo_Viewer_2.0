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
        val extractedPages = java.lang.StringBuilder()
        
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                document = PDDocument.load(inputStream)
                
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true 
                    suppressDuplicateOverlappingText = true
                }

                // CRITICAL ARCHITECTURE SHIFT: Iterate page-by-page.
                // Prevents OOM crashes on massive legacy documents like the 580+ page N64 manual.
                val numPages = document.numberOfPages
                for (page in 1..numPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    
                    val rawPageText = stripper.getText(document) ?: ""
                    val cleanedText = cleanExtractedText(rawPageText)
                    
                    // Only append if the page actually contains usable information.
                    // This strips out the trailing blank pages in the N64 manual.
                    if (cleanedText.isNotBlank()) {
                        extractedPages.append("--- SOURCE PAGE ").append(page).append(" ---\n")
                        extractedPages.append(cleanedText).append("\n\n")
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("PdfProcessor", "Memory limit exceeded during extraction: ${e.message}")
            extractedPages.append("\nSYSTEM_NOTE: Memory limit reached. Partial extraction recovered.")
        } catch (e: Exception) {
            Log.e("PdfProcessor", "Error parsing PDF: ${e.message}")
        } finally {
            document?.close()
        }

        return if (extractedPages.isEmpty()) {
            "SYSTEM_NOTE: This document appears to be empty or requires OCR. No readable text was extracted."
        } else {
            extractedPages.toString()
        }
    }

    private fun cleanExtractedText(rawText: String): String {
        val lines = rawText.lines()
        val cleanedLines = mutableListOf<String>()
        val tocRegex = Regex("[\\.]{4,}")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (tocRegex.containsMatchIn(trimmed)) continue
            
            // Broadened regex to support legacy technical documentation symbols (\t included)
            val sanitizedLine = trimmed.replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF\\n\\r\\t]"), "")
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
        
        return chunks.ifEmpty { listOf("SYSTEM_NOTE: Chunking failed, text unreadable.") }
    }
}
