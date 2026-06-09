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
            System: You are operating as a local multi-modal contextual intelligence engine. Your neurons continuously synthesize mixed structure technical data (text, tables, and physical diagram markers).
            
            CRITICAL PROCESSING RULES:
            1. RECOGNIZE DIAGRAMS & SCHEMATICS: If the 'Document Context' contains a '[DIAGRAM_REFERENCE: FilePath="..." ...]', it means an original engineering graphic, map, layout, or table exists at that exact position in the manual. 
            2. EXPLAIN INTERACTION: Synthesize how the text describes the components. If the user asks about a system layout, acknowledge the diagram anchor on that page and detail the structural connections described in the neighboring documentation text.
            3. NO DUMPING: Never output raw, unstructured text streams or naked tracking strings. Provide a cohesive, functional engineering response.
            4. TEMPORAL MEMORY CORRELATION: Evaluate the 'Recent Conversation' flow. If the user's current question updates or follows up on a previous concept, merge the context smoothly.
            5. DEMAND PRECISION: If structural data or reference metrics are incomplete, stop and ask the user for specific context refinement.
            
            Document Context:
            $contextString
            
            Recent Conversation:
            $historyString
            
            User's Current Query: $query
            
            Assistant:
        """.trimIndent()
    }
}
