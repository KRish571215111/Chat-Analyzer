package com.example.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY exportDate DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    fun getSessionById(id: Long): Flow<ChatSession?>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionByIdSync(id: Long): ChatSession?

    @Query("SELECT * FROM chat_sessions WHERE fileHash = :hash")
    suspend fun getSessionByHashSync(hash: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Query("UPDATE chat_sessions SET totalMessages = :totalMessages, dateStart = :dateStart, dateEnd = :dateEnd WHERE id = :id")
    suspend fun updateSessionStats(id: Long, totalMessages: Int, dateStart: Long, dateEnd: Long)
    
    @Query("""
        UPDATE chat_sessions SET 
            participantCount = :participantCount, 
            mediaFileCount = :mediaFileCount,
            extractedFileCount = :extractedFileCount
        WHERE id = :id
    """)
    suspend fun updateSessionMetadata(id: Long, participantCount: Int, mediaFileCount: Int, extractedFileCount: Int)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants WHERE sessionId = :sessionId ORDER BY messageCount DESC")
    fun getParticipantsForSession(sessionId: Long): Flow<List<Participant>>

    @Query("SELECT * FROM participants WHERE sessionId = :sessionId ORDER BY messageCount DESC")
    suspend fun getParticipantsForSessionSync(sessionId: Long): List<Participant>

    @Query("SELECT * FROM participants WHERE sessionId = :sessionId AND name = :name LIMIT 1")
    suspend fun getParticipantByName(sessionId: Long, name: String): Participant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: Participant): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<Participant>)

    @Update
    suspend fun updateParticipant(participant: Participant)

    @Query("UPDATE participants SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavoriteParticipant(id: Long, isFavorite: Boolean)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT 50000")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT 50000")
    suspend fun getMessagesForSessionSync(sessionId: Long): List<ChatMessage>
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessagesCountForSessionSync(sessionId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionPaged(sessionId: Long): androidx.paging.PagingSource<Int, ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForSessionPaged(sessionId: Long, limit: Int, offset: Int): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("""
        SELECT chat_messages.* FROM chat_messages 
        JOIN chat_messages_fts ON chat_messages.id = chat_messages_fts.rowid 
        WHERE chat_messages_fts MATCH :query AND chat_messages.sessionId = :sessionId 
        ORDER BY chat_messages.timestamp ASC
    """)
    suspend fun searchMessages(sessionId: Long, query: String): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedMessages(sessionId: Long): Flow<List<ChatMessage>>

    @Query("UPDATE chat_messages SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun toggleBookmark(id: Long, isBookmarked: Boolean)

    @Query("UPDATE chat_messages SET isPinned = :isPinned WHERE id = :id")
    suspend fun togglePin(id: Long, isPinned: Boolean)

    @RawQuery
    suspend fun filterMessagesRaw(query: SupportSQLiteQuery): List<ChatMessage>

    @RawQuery(observedEntities = [ChatMessage::class])
    fun filterMessagesRawPaged(query: SupportSQLiteQuery): androidx.paging.PagingSource<Int, ChatMessage>
}

@Dao
interface AiSummaryDao {
    @Query("SELECT * FROM ai_summaries WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getSummariesForSession(sessionId: Long): Flow<List<AiSummary>>

    @Query("SELECT * FROM ai_summaries WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getSummariesForSessionSync(sessionId: Long): List<AiSummary>

    @Query("SELECT * FROM ai_summaries WHERE sessionId = :sessionId AND category = :category AND participantName IS NULL LIMIT 1")
    suspend fun getSummaryByCategorySync(sessionId: Long, category: String): AiSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: AiSummary): Long

    @Query("DELETE FROM ai_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummariesForSession(sessionId: Long)

    @Query("DELETE FROM ai_summaries")
    suspend fun deleteAllSummaries()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearches(limit: Int): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM extracted_media WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMediaFilesForSession(sessionId: Long): Flow<List<MediaFile>>

    @Query("SELECT * FROM extracted_media WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMediaFilesForSessionSync(sessionId: Long): List<MediaFile>

    @Query("SELECT COUNT(*) FROM extracted_media WHERE sessionId = :sessionId")
    suspend fun getMediaFilesCountForSessionSync(sessionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaFiles(mediaFiles: List<MediaFile>)

    @Update
    suspend fun updateMediaFile(mediaFile: MediaFile)

    @Query("DELETE FROM extracted_media WHERE sessionId = :sessionId")
    suspend fun deleteMediaFilesForSession(sessionId: Long)
}

@Database(
    entities = [
        ChatSession::class,
        Participant::class,
        ChatMessage::class,
        ChatMessageFts::class,
        AiSummary::class,
        SearchHistory::class,
        MediaFile::class,
        Link::class,
        EmojiStatistic::class,
        WordFrequency::class,
        ConversationTopic::class,
        Poll::class,
        PollOption::class,
        DailyStatistic::class,
        MonthlyStatistic::class,
        Workspace::class,
        Tag::class,
        Annotation::class,
        HourlyHeatmap::class,
        WeekdayHeatmap::class,
        GeneratedReport::class,
        AppSettings::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun participantDao(): ParticipantDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun aiSummaryDao(): AiSummaryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun mediaFileDao(): MediaFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "group_analyzer_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
