package com.explorer.ai.domain

import com.explorer.ai.ui.ChatState

object RagPromptBuilder {
    
    fun buildPrompt(query: String, retrievedContext: List<String>, chatHistory: List<ChatState.Message>): String {
        // ... (existing context gathering)
        
        return """
            ... [PREVIOUS DIRECTIVES] ...
            
            DIAGRAMMING DIRECTIVE:
            If the user asks about system architecture, memory layouts, or operational flows, and you have context for it:
            1. Provide the textual explanation.
            2. End your response with a clear trigger token: [DIAGRAM_TRIGGER:MEMORY_MAP] or [DIAGRAM_TRIGGER:ARCH_FLOW].
            
            Document Context: ${retrievedContext.joinToString("\n")}
            User Query: $query
            Assistant:
        """.trimIndent()
    }
}
