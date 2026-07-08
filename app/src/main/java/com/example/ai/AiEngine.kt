package com.example.ai

import com.example.data.ChatMessage
import com.example.data.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- AI Architecture ---

interface PromptBuilder {
    fun buildPrompt(context: String, query: String): String
}

interface ConversationContextBuilder {
    fun build(messages: List<ChatMessage>, participants: List<Participant>): String
}

interface MediaContextBuilder {
    fun build(mediaMessages: List<ChatMessage>): String
}

interface OcrEngine {
    suspend fun extractText(imageBytes: ByteArray): String
}

interface SummaryEngine {
    suspend fun generateSummary(context: String): String
}

interface QuestionAnsweringEngine {
    suspend fun answer(question: String, context: String): String
}

interface TopicDetectionEngine {
    suspend fun detectTopics(context: String): List<String>
}

interface EntityExtractionEngine {
    suspend fun extractEntities(context: String): List<String>
}

interface RecommendationEngine {
    suspend fun getRecommendations(context: String): List<String>
}

interface ResponseFormatter {
    fun format(response: String): String
}

interface ErrorHandler {
    fun handleError(e: Exception): String
}

// --- Implementations ---

class DefaultConversationContextBuilder : ConversationContextBuilder {
    override fun build(messages: List<ChatMessage>, participants: List<Participant>): String {
        val sb = StringBuilder()
        sb.appendLine("Chat Context:")
        sb.appendLine("Total Participants: ${participants.size}")
        sb.appendLine("Total Messages: ${messages.size}")
        
        val messageChunk = messages.take(150)
        sb.appendLine("\nRecent Messages (Chunk of 150 max):")
        messageChunk.forEach { msg ->
            sb.appendLine("${msg.timestamp} - ${msg.senderName}: ${msg.messageText}")
        }
        return sb.toString()
    }
}

class DefaultPromptBuilder : PromptBuilder {
    override fun buildPrompt(context: String, query: String): String {
        return """
            You are an expert AI Analyst analyzing a WhatsApp chat export.
            You must always remain grounded in the actual exported chat data provided.
            Never fabricate statistics, conversations, or participants.
            Whenever uncertainty exists, explicitly state it instead of guessing.

            $context

            User Query: $query
            
            Provide a clear, formatted, and accurate response based ONLY on the context provided above.
        """.trimIndent()
    }
}

class DefaultErrorHandler : ErrorHandler {
    override fun handleError(e: Exception): String {
        return "AI Error: ${e.localizedMessage ?: "Unknown error occurred."}. Please verify settings."
    }
}

object AiEngine {
    val promptBuilder: PromptBuilder = DefaultPromptBuilder()
    val contextBuilder: ConversationContextBuilder = DefaultConversationContextBuilder()
    val errorHandler: ErrorHandler = DefaultErrorHandler()

    suspend fun processQuery(messages: List<ChatMessage>, participants: List<Participant>, query: String, apiKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isEmpty() || apiKey == "YOUR_ZAI_API_KEY_HERE") {
                    return@withContext Result.failure(Exception("AI API Key is missing or default. Please configure in Settings."))
                }
                
                val context = contextBuilder.build(messages, participants)
                val finalPrompt = promptBuilder.buildPrompt(context, query)
                
                // Simulated execution
                kotlinx.coroutines.delay(1500)
                Result.success("Simulated grounded AI response for: '$query'.\n(Note: Connect actual API for live results. Context loaded: ${messages.take(150).size} messages.)")
            } catch (e: Exception) {
                Result.failure(Exception(errorHandler.handleError(e)))
            }
        }
    }
}
