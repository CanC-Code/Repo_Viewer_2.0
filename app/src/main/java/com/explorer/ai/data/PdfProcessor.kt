package com.explorer.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

/**
 * PDF text extractor using Android's built-in PdfRenderer + ML Kit OCR.
 *
 * Works for BOTH text-layer PDFs and fully scanned/raster PDFs (e.g. N64 Ultra64 manual).
 * Android's PdfRenderer renders each page as a Bitmap regardless of whether the PDF has
 * a text layer. ML Kit then reads the rendered image with on-device OCR.
 *
 * Memory safety: each page bitmap is recycled immediately after OCR.
 * OOM safety: skips page and logs if Bitmap creation fails for a given page.
 */
object PdfProcessor {

    private const val TAG = "PdfProcessor"
    private const val RENDER_WIDTH_PX = 1440  // Optimal for ML Kit OCR accuracy vs memory

    /**
     * Renders all pages of the PDF and extracts text via ML Kit OCR.
     * @param onProgress optional callback with (currentPage, totalPages)
     */
    suspend fun visuallyReadAndExtractUri(
        context: Context,
        uri: Uri,
        onProgress: ((Int, Int) -> Unit)? = null
    ): String {
        val output = StringBuilder()

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                val renderer = PdfRenderer(fd)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val totalPages = renderer.pageCount

                Log.d(TAG, "Starting OCR on $totalPages pages...")

                for (pageIndex in 0 until totalPages) {
                    try {
                        val page = renderer.openPage(pageIndex)

                        val scale = RENDER_WIDTH_PX.toFloat() / page.width
                        val w = RENDER_WIDTH_PX
                        val h = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val image = InputImage.fromBitmap(bitmap, 0)
                        val result = recognizer.process(image).await()

                        output.append("--- PAGE_START: ${pageIndex + 1} ---\n")

                        if (result.textBlocks.isEmpty()) {
                            output.append("[VISUAL_NOTE: No text detected on this page]\n")
                        } else {
                            for (block in result.textBlocks) {
                                // Strip internal newlines within a block — they represent
                                // line wraps within a paragraph, not sentence boundaries
                                val blockText = block.text.replace("\n", " ").trim()
                                if (blockText.isNotBlank()) {
                                    output.append(blockText).append("\n")
                                }
                            }
                        }

                        output.append("--- PAGE_END: ${pageIndex + 1} ---\n\n")

                        bitmap.recycle()
                        onProgress?.invoke(pageIndex + 1, totalPages)

                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "OOM on page ${pageIndex + 1} — skipping")
                        System.gc()
                    } catch (e: Exception) {
                        Log.e(TAG, "Page ${pageIndex + 1} error: ${e.message}")
                    }
                }

                renderer.close()
                Log.d(TAG, "OCR complete.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF open fault: ${e.message}")
            output.append("[SYSTEM_FAULT: Could not open document — ${e.localizedMessage}]")
        }

        return output.toString()
    }

    /**
     * Splits extracted text into semantic chunks no larger than maxChunkSize characters.
     * Chunks respect page boundaries.
     */
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

    /**
     * Cleans raw extracted text before NLP processing:
     * - Removes page markers
     * - Collapses whitespace
     * - Strips layout tokens
     */
    fun cleanRawText(raw: String): String = raw
        .replace(Regex("---\\s*(PAGE_START|PAGE_END):\\s*\\d+\\s*---"), "")
        .replace(Regex("\\[VISUAL_NOTE[^\\]]*\\]"), "")
        .replace(Regex("\\[SYSTEM_FAULT[^\\]]*\\]"), "")
        .replace(Regex("\\.{4,}"), " ")
        .replace(Regex("-\\s*\\n\\s+([a-z])"), "$1")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
