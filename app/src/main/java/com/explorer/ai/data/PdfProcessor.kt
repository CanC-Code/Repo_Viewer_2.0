package com.explorer.ai.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.lang.StringBuilder

/**
 * Standard local file processor.
 * Extracts basic string representations by reading raw bytes and filtering 
 * readable ASCII/UTF-8 character bounds, eliminating external API dependencies.
 */
object PdfProcessor {

    private const val TAG = "PdfProcessor"

    suspend fun visuallyReadAndExtractUri(
        context: Context,
        uri: Uri,
        onProgress: ((Int, Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val output = StringBuilder()

        try {
            Log.d(TAG, "Starting raw heuristic extraction on document...")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val extractedText = extractPrintableText(inputStream)
                output.append("--- PAGE_START: 1 ---\n")
                output.append(extractedText)
                output.append("\n--- PAGE_END: 1 ---\n\n")
            }
            
            onProgress?.invoke(1, 1)
            Log.d(TAG, "Extraction complete.")

        } catch (e: Exception) {
            Log.e(TAG, "Document open fault: ${e.message}")
            output.append("[SYSTEM_FAULT: Could not read document stream — ${e.localizedMessage}]")
        }

        return@withContext output.toString()
    }

    private fun extractPrintableText(inputStream: InputStream): String {
        val buffer = ByteArray(1024 * 8)
        val sb = StringBuilder()
        var bytesRead: Int
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            for (i in 0 until bytesRead) {
                val charCode = buffer[i].toInt()
                // Basic heuristic: Keep standard printable ASCII characters, newlines, and tabs
                if ((charCode in 32..126) || charCode == 10 || charCode == 9) {
                    sb.append(charCode.toChar())
                }
            }
        }
        
        // Strip multiple redundant newlines formed from raw binary gap stripping
        return sb.toString().replace(Regex("\\n{3,}"), "\n\n")
    }

    fun chunkTextSemantically(text: String, maxChunkSize: Int = 1200): List<String> {
        val blocks = text.split(Regex("(?=--- PAGE_START)"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (block in blocks) {
            if (current.length + block.length > maxChunkSize && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            current.append(block).append("\n")
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks.ifEmpty { listOf(text) }
    }

    fun cleanRawText(raw: String): String = raw
        .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), "")
        .replace(Regex("\\[SYSTEM_FAULT[^\\]]*\\]"), "")
        .replace(Regex("\\.{4,}"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
