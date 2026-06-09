package com.explorer.ai.domain

import com.explorer.ai.ui.ChatState

object RagPromptBuilder {
    
    fun buildPrompt(
        query: String, 
        retrievedContext: List<String>, 
        chatHistory: List<ChatState.Message>
    ): String {
        val contextString = retrievedContext.joinToString("\n---\n") { it.trim() }
        
        val historyString = chatHistory.takeLast(6).joinToString("\n") { 
            "${if (it.isUser) "User" else "Assistant"}: ${it.text}"
        }

        return """
            System: You are operating as a local multi-modal contextual intelligence engine tied directly to an NPU. You must interpret layout geometry, spacing, and structural markers accurately.
            
            SPATIAL PROCESSING RULES:
            1. READ THE GEOMETRY: The 'Document Context' contains layout markers like [PARAGRAPH_START], [COLUMN_START], and --- PAGE_START ---. Use these to understand the physical flow of the text. Do not ignore paragraph groupings.
            2. ANCHOR TO VISUALS: If you detect a [VISUAL_ANCHOR: File="..." SpatialContext="..."], it means a physical schematic exists at that exact coordinate in the text. You must synthesize the 'SpatialContext' string to explain what the diagram illustrates.
            3. SYNTHESIZE, DO NOT DUMP: Explain the engineering concepts smoothly. Never output raw data dumps, raw PDF brackets, or naked file paths to the user.
            4. REQUIRE PRECISION: If the context is fragmented, stop and ask the user for specific refinement.
            
            DIAGRAMMING DIRECTIVE:
            If the user asks to learn about or understand hardware architecture, memory layouts, or operational flows, and the context supports it:
            1. Provide the textual explanation first, referencing any [VISUAL_ANCHOR]s found in the layout.
            2. End your response with a precise internal trigger token to command the GUI: [DIAGRAM_TRIGGER:MEMORY_MAP] or [DIAGRAM_TRIGGER:ARCH_FLOW].
            
            Document Context:
            $contextString
            
            Recent Conversation:
            $historyString
            
            User's Current Query: $query
            
            Assistant:
        """.trimIndent()
    }
}
