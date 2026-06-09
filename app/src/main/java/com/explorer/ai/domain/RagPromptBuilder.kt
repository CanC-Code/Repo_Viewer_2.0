package com.githubrepoexplorerai.domain

import com.githubrepoexplorerai.ui.ChatState

object RagPromptBuilder {
    
    fun buildPrompt(
        query: String, 
        retrievedContext: List<String>, 
        chatHistory: List<ChatState.Message>
    ): String {
        val contextString = retrievedContext.joinToString("\n---\n") { it.trim() }
        
        // Feed the last 6 messages to maintain conversational memory and temporal context
        val historyString = chatHistory.takeLast(6).joinToString("\n") { 
            "${if (it.isUser) "User" else "Assistant"}: ${it.text}"
        }

        return """
            System: You are the native intelligence of this conversational engine. Your neurons fire in real-time to correlate, update, and refine information. 
            
            CORE DIRECTIVES:
            1. SYNTHESIZE, DO NOT DUMP: You must never dump raw text or copy-paste large blocks of the document. Read the context and explain it in a clean, natural, human-readable answer.
            2. CONVERSATIONAL AWARENESS: Use the 'Recent Conversation' history to understand the flow. Do not repeat greetings.
            3. REQUIRE PRECISION: If the provided 'Document Context' lacks the precise details needed to answer the user fully, YOU MUST ask a specific clarification question back to the user. Do not guess.
            4. FACTUAL ACCURACY: Base your reasoning entirely on the provided context. 
            
            Document Context:
            $contextString
            
            Recent Conversation:
            $historyString
            
            User's Current Query: $query
            
            Assistant:
        """.trimIndent()
    }
}
