package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val exportDate: Long = System.currentTimeMillis(),
    val totalMessages: Int = 0,
    val dateStart: Long = 0L,
    val dateEnd: Long = 0L,
    val filePath: String = "",
    val fileHash: String = "",
    
    // Detailed Metadata
    val originalFileName: String = "",
    val originalFileSize: Long = 0L,
    val detectedLanguage: String = "Unknown",
    val textEncoding: String = "UTF-8",
    val extractedFileCount: Int = 0,
    val participantCount: Int = 0,
    val mediaFileCount: Int = 0,
    
    // Core Statistics
    val activeParticipantsCount: Int = 0,
    val inactiveParticipantsCount: Int = 0,
    val imagesCount: Int = 0,
    val videosCount: Int = 0,
    val audioCount: Int = 0,
    val voiceNotesCount: Int = 0,
    val gifsCount: Int = 0,
    val documentsCount: Int = 0,
    val locationsCount: Int = 0,
    val contactsCount: Int = 0,
    val linksCount: Int = 0,
    val pollsCount: Int = 0,
    val callsCount: Int = 0,
    val averageMessageLength: Float = 0f,
    val averageDailyMessages: Float = 0f,
    val averageWeeklyMessages: Float = 0f,
    val averageMonthlyMessages: Float = 0f,
    val longestConversationDuration: Long = 0L,
    val shortestConversationDuration: Long = 0L,
    val longestMessageLength: Int = 0,
    val shortestMessageLength: Int = 0,
    val totalWordsCount: Int = 0,
    val uniqueWordsCount: Int = 0,
    val emojisCount: Int = 0,
    val questionsCount: Int = 0,
    val repliesCount: Int = 0,
    val mentionsCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "name"], unique = true)]
)
data class Participant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val name: String,
    val phone: String? = null,
    val normalizedPhone: String? = null,
    val messageCount: Int = 0,
    val wordCount: Int = 0,
    val characterCount: Int = 0,
    val mediaCount: Int = 0,
    val emojiCount: Int = 0,
    val linkCount: Int = 0,
    
    // New Media Stats
    val imagesShared: Int = 0,
    val videosShared: Int = 0,
    val documentsShared: Int = 0,
    val voiceNotesShared: Int = 0,
    val audioShared: Int = 0,
    val gifsShared: Int = 0,
    val stickersShared: Int = 0,
    val contactsShared: Int = 0,
    val locationsShared: Int = 0,
    
    // Conversation Stats
    val questionsAsked: Int = 0,
    val repliesGiven: Int = 0,
    val averageMessageLength: Float = 0f,
    val averageDailyActivity: Float = 0f,
    
    // Most Active periods
    val mostActiveHour: Int = -1,
    val mostActiveWeekday: Int = -1,
    val mostActiveMonth: String? = null,
    
    // Favorites
    val favoriteEmoji: String? = null,
    val mostUsedWord: String? = null,
    
    // AI and Scoring
    val personalitySummary: String? = null,
    val sentimentScore: Float? = null,
    val activityScore: Float = 0f,
    val contributionPercentage: Float = 0f,
    
    val firstActive: Long = 0L,
    val lastActive: Long = 0L,
    val isFavorite: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]), 
        Index(value = ["senderName"]), 
        Index(value = ["timestamp"]),
        Index(value = ["messageType"]),
        Index(value = ["isMedia"]),
        Index(value = ["isBookmarked"])
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val senderName: String,
    val timestamp: Long,
    val messageText: String, // Normalized text
    val rawMessageText: String = "", // Original unnormalized text
    val messageType: String = "TEXT", // TEXT, IMAGE, VIDEO, AUDIO, GIF, STICKER, DOCUMENT, LOCATION, CALL, DELETED, SYSTEM, POLL, VOICE_NOTE
    val isMedia: Boolean = false,
    val isDeleted: Boolean = false,
    val isSystem: Boolean = false,
    val isBookmarked: Boolean = false,
    val isPinned: Boolean = false
)

@androidx.room.Fts4(contentEntity = ChatMessage::class)
@Entity(tableName = "chat_messages_fts")
data class ChatMessageFts(
    @androidx.room.ColumnInfo(name = "messageText") val messageText: String,
    @androidx.room.ColumnInfo(name = "senderName") val senderName: String
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "ai_summaries",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class AiSummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val participantName: String? = null, // If null, general chat summary
    val category: String, // e.g. "GENERAL", "DECISIONS", "FAIRNESS", "PERSONALITY", "WEEKLY"
    val summaryText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "extracted_media",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["fileType"])]
)
data class MediaFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val messageId: Long? = null,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val fileType: String, // "IMAGE", "VIDEO", "AUDIO", "VOICE", "DOCUMENT", "STICKER", "GIF"
    val senderName: String,
    val timestamp: Long,
    val aiCaption: String? = null,
    val ocrText: String? = null,
    val isScreenshot: Boolean = false,
    val isMeme: Boolean = false,
    val isDocument: Boolean = false,
    val isChart: Boolean = false,
    val isQrCode: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "links", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId"])])
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val messageId: Long? = null,
    val url: String,
    val domain: String,
    val protocol: String,
    val senderName: String = "",
    val timestamp: Long = 0L
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "emoji_statistics", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "emoji"], unique = true)])
data class EmojiStatistic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val emoji: String,
    val count: Int = 0,
    val firstUsed: Long = 0L,
    val lastUsed: Long = 0L
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "word_frequency", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "word"], unique = true)])
data class WordFrequency(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val word: String,
    val count: Int = 0,
    val isStopWord: Boolean = false
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "conversation_topics", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId"])])
data class ConversationTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val topicName: String,
    val messageCount: Int = 0,
    val firstMentioned: Long = 0L,
    val lastMentioned: Long = 0L
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "polls", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId"])])
data class Poll(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val messageId: Long? = null,
    val question: String,
    val creatorName: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "poll_options", foreignKeys = [ForeignKey(entity = Poll::class, parentColumns = ["id"], childColumns = ["pollId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["pollId"])])
data class PollOption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pollId: Long,
    val optionText: String,
    val voteCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "daily_statistics", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "dateDay"], unique = true)])
data class DailyStatistic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val dateDay: Long, // e.g. timestamp rounded to start of day
    val messageCount: Int = 0,
    val mediaCount: Int = 0,
    val linkCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "monthly_statistics", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "yearMonth"], unique = true)])
data class MonthlyStatistic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val yearMonth: String, // e.g. "2023-10"
    val messageCount: Int = 0,
    val mediaCount: Int = 0,
    val linkCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "hourly_heatmaps", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "hourOfDay"], unique = true)])
data class HourlyHeatmap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val hourOfDay: Int, // 0 to 23
    val messageCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "weekday_heatmaps", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId", "dayOfWeek"], unique = true)])
data class WeekdayHeatmap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val dayOfWeek: Int, // 1 to 7
    val messageCount: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "generated_reports", foreignKeys = [ForeignKey(entity = ChatSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["sessionId"])])
data class GeneratedReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val reportType: String, // PDF, HTML, CSV, JSON
    val filePath: String,
    val generationTime: Long = System.currentTimeMillis(),
    val checksum: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val themePreference: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val amoledDark: Boolean = false,
    val highContrast: Boolean = false,
    val fontScale: Float = 1.0f,
    val secureStorage: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isOnboardingComplete: Boolean = false
)
