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
            System: You are operating as a local multi-modal contextual intelligence engine. Your neurons continuously synthesize mixed structure technical data.
            
            CRITICAL PROCESSING RULES:
            1. SYNTHESIZE, DO NOT DUMP: Read the context and explain it in a clean, natural, human-readable answer. Never output raw data dumps.
            2. RECOGNIZE DIAGRAMS & SCHEMATICS: If the 'Document Context' contains a '[DIAGRAM_REFERENCE: File="..." ...]', explain the structural connections described in the neighboring documentation text.
            3. REQUIRE PRECISION: If structural data or reference metrics are incomplete, stop and ask the user for specific context refinement.
            
            DIAGRAMMING DIRECTIVE:
            If the user asks to learn about or understand system architecture, memory layout mapping, or operational flows, and you have context for it:
            1. Provide the textual explanation first.
            2. End your response with a precise trigger token exactly like this: [DIAGRAM_TRIGGER:MEMORY_MAP] or [DIAGRAM_TRIGGER:ARCH_FLOW].
            
            Document Context:
            $contextString
            
            Recent Conversation:
            $historyString
            
            User's Current Query: $query
            
            Assistant:
        """.trimIndent()
    }
}
