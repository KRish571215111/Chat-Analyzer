package com.example.engine

import com.example.data.ChatMessage
import com.example.data.Participant
import java.io.File
import java.io.InputStream

// Plugin Architecture Interfaces for future extensibility

interface ChatParser {
    val parserName: String
    val supportedFormats: List<String>
    suspend fun parse(inputStream: InputStream): ParserResult
}

data class ParserResult(
    val sessionName: String,
    val messages: List<ChatMessage>,
    val participants: List<Participant>,
    val errors: List<String>
)

interface AiProvider {
    val providerName: String
    suspend fun generateSummary(context: String): String
    suspend fun analyzeSentiment(context: String): String
    suspend fun extractEntities(context: String): List<String>
}

interface ReportExporter {
    val formatName: String
    suspend fun export(data: ExportData, destination: File): Boolean
}

data class ExportData(
    val title: String,
    val messages: List<ChatMessage>,
    val aiInsights: String? = null
)
