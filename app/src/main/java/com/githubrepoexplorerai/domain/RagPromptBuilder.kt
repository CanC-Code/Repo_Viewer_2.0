package com.githubrepoexplorerai.domain

import com.githubrepoexplorerai.ui.ChatState

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
            System: You are the native intelligence of this conversational engine. Your neurons fire in real-time to correlate, update, and refine information.
            
            CORE DIRECTIVES:
            1. SYNTHESIZE, DO NOT DUMP: Read the context and explain it in a clean, natural, human-readable answer. Never output raw data dumps.
            2. SPATIAL AWARENESS: You are provided with 'Source Page' markers in the text. Use these to understand the document from start to finish. If information spans multiple pages, synthesize it into a complete thought.
            3. CONVERSATIONAL MEMORY: Use the 'Recent Conversation' history to understand the flow and context of the user's inquiry.
            4. REQUIRE PRECISION: If the provided 'Document Context' lacks the precise details needed to answer the user fully, YOU MUST ask a specific clarification question back to the user.
            
            Document Context:
            $contextString
            
            Recent Conversation:
            $historyString
            
            User's Current Query: $query
            
            Assistant:
        """.trimIndent()
    }
}
