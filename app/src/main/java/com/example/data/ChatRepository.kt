package com.example.data

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val database: AppDatabase) {
    private val sessionDao = database.chatSessionDao()
    private val participantDao = database.participantDao()
    private val messageDao = database.chatMessageDao()
    private val aiSummaryDao = database.aiSummaryDao()
    private val searchDao = database.searchHistoryDao()
    private val mediaFileDao = database.mediaFileDao()

    val allSessions: Flow<List<ChatSession>> = sessionDao.getAllSessions()

    fun getSessionById(id: Long): Flow<ChatSession?> = sessionDao.getSessionById(id)
    suspend fun getSessionByIdSync(id: Long): ChatSession? = sessionDao.getSessionByIdSync(id)
    suspend fun getSessionByHashSync(hash: String): ChatSession? = sessionDao.getSessionByHashSync(hash)

    fun getMediaFilesForSession(sessionId: Long): Flow<List<MediaFile>> =
        mediaFileDao.getMediaFilesForSession(sessionId)

    suspend fun getMediaFilesForSessionSync(sessionId: Long): List<MediaFile> =
        mediaFileDao.getMediaFilesForSessionSync(sessionId)

    suspend fun getMediaCountSync(sessionId: Long): Int =
        mediaFileDao.getMediaFilesCountForSessionSync(sessionId)

    suspend fun insertMediaFiles(mediaFiles: List<MediaFile>) = withContext(Dispatchers.IO) {
        mediaFileDao.insertMediaFiles(mediaFiles)
    }

    suspend fun updateMediaFile(mediaFile: MediaFile) = withContext(Dispatchers.IO) {
        mediaFileDao.updateMediaFile(mediaFile)
    }

    suspend fun deleteMediaFilesForSession(sessionId: Long) = withContext(Dispatchers.IO) {
        mediaFileDao.deleteMediaFilesForSession(sessionId)
    }

    fun getParticipants(sessionId: Long): Flow<List<Participant>> =
        participantDao.getParticipantsForSession(sessionId)

    suspend fun getParticipantsSync(sessionId: Long): List<Participant> =
        participantDao.getParticipantsForSessionSync(sessionId)

    fun getMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messageDao.getMessagesForSession(sessionId)

    fun getMessagesPaged(sessionId: Long): Flow<androidx.paging.PagingData<ChatMessage>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 50, enablePlaceholders = true),
            pagingSourceFactory = { messageDao.getMessagesForSessionPaged(sessionId) }
        ).flow
    }

    suspend fun getMessagesSync(sessionId: Long): List<ChatMessage> =
        messageDao.getMessagesForSessionSync(sessionId)

    suspend fun getMessagesCountSync(sessionId: Long): Int =
        messageDao.getMessagesCountForSessionSync(sessionId)

    fun getBookmarkedMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messageDao.getBookmarkedMessages(sessionId)

    fun getSummaries(sessionId: Long): Flow<List<AiSummary>> =
        aiSummaryDao.getSummariesForSession(sessionId)

    suspend fun getSummariesSync(sessionId: Long): List<AiSummary> = withContext(Dispatchers.IO) {
        aiSummaryDao.getSummariesForSessionSync(sessionId)
    }

    fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistory>> =
        searchDao.getRecentSearches(limit)

    suspend fun createSession(
        name: String, 
        filePath: String, 
        fileHash: String = "",
        originalFileName: String = "",
        originalFileSize: Long = 0L,
        textEncoding: String = "UTF-8",
        detectedLanguage: String = "Unknown"
    ): Long = withContext(Dispatchers.IO) {
        sessionDao.insertSession(
            ChatSession(
                name = name, 
                filePath = filePath, 
                fileHash = fileHash,
                originalFileName = originalFileName,
                originalFileSize = originalFileSize,
                textEncoding = textEncoding,
                detectedLanguage = detectedLanguage
            )
        )
    }

    suspend fun updateSessionStats(id: Long, totalMessages: Int, dateStart: Long, dateEnd: Long) = withContext(Dispatchers.IO) {
        sessionDao.updateSessionStats(id, totalMessages, dateStart, dateEnd)
    }

    suspend fun updateSessionMetadata(id: Long, participantCount: Int, mediaFileCount: Int, extractedFileCount: Int) = withContext(Dispatchers.IO) {
        sessionDao.updateSessionMetadata(id, participantCount, mediaFileCount, extractedFileCount)
    }

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        sessionDao.deleteSession(id)
    }

    suspend fun insertParticipants(participants: List<Participant>) = withContext(Dispatchers.IO) {
        participantDao.insertParticipants(participants)
    }

    suspend fun updateParticipant(participant: Participant) = withContext(Dispatchers.IO) {
        participantDao.updateParticipant(participant)
    }

    suspend fun toggleFavoriteParticipant(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        participantDao.toggleFavoriteParticipant(id, isFavorite)
    }

    suspend fun insertMessagesInBatches(
        messages: List<ChatMessage>,
        onProgress: ((progress: Float) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val chunkSize = 1000
        val chunks = messages.chunked(chunkSize)
        val totalChunks = chunks.size
        
        chunks.forEachIndexed { index, chunk ->
            messageDao.insertMessages(chunk)
            onProgress?.invoke((index + 1).toFloat() / totalChunks)
        }
    }

    suspend fun toggleBookmark(messageId: Long, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        messageDao.toggleBookmark(messageId, isBookmarked)
    }

    suspend fun togglePin(messageId: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        messageDao.togglePin(messageId, isPinned)
    }

    suspend fun insertSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchDao.insertSearch(SearchHistory(query = query))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchDao.clearSearchHistory()
    }

    suspend fun insertSummary(summary: AiSummary): Long = withContext(Dispatchers.IO) {
        aiSummaryDao.insertSummary(summary)
    }

    suspend fun getSummaryByCategory(sessionId: Long, category: String): AiSummary? = withContext(Dispatchers.IO) {
        aiSummaryDao.getSummaryByCategorySync(sessionId, category)
    }

    suspend fun deleteAllSummaries() = withContext(Dispatchers.IO) {
        aiSummaryDao.deleteAllSummaries()
    }

    suspend fun searchMessages(sessionId: Long, query: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val ftsQuery = if (query.isNotBlank()) "$query*" else ""
        messageDao.searchMessages(sessionId, ftsQuery)
    }

    suspend fun filterMessages(
        sessionId: Long,
        sender: String? = null,
        dateStart: Long? = null,
        dateEnd: Long? = null,
        type: String? = null,
        isBookmarked: Boolean? = null,
        query: String? = null
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        val sqliteQuery = buildFilterQuery(sessionId, sender, dateStart, dateEnd, type, isBookmarked, query)
        messageDao.filterMessagesRaw(sqliteQuery)
    }

    fun filterMessagesPaged(
        sessionId: Long,
        sender: String? = null,
        dateStart: Long? = null,
        dateEnd: Long? = null,
        type: String? = null,
        isBookmarked: Boolean? = null,
        query: String? = null
    ): Flow<androidx.paging.PagingData<ChatMessage>> {
        val sqliteQuery = buildFilterQuery(sessionId, sender, dateStart, dateEnd, type, isBookmarked, query)
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 50, enablePlaceholders = true),
            pagingSourceFactory = { messageDao.filterMessagesRawPaged(sqliteQuery) }
        ).flow
    }

    private fun buildFilterQuery(
        sessionId: Long,
        sender: String? = null,
        dateStart: Long? = null,
        dateEnd: Long? = null,
        type: String? = null,
        isBookmarked: Boolean? = null,
        query: String? = null
    ): SimpleSQLiteQuery {
        val useFts = query != null && query.isNotBlank()
        val queryBuilder = java.lang.StringBuilder()
        
        if (useFts) {
            queryBuilder.append("SELECT chat_messages.* FROM chat_messages ")
            queryBuilder.append("JOIN chat_messages_fts ON chat_messages.id = chat_messages_fts.rowid ")
            queryBuilder.append("WHERE chat_messages.sessionId = $sessionId ")
        } else {
            queryBuilder.append("SELECT * FROM chat_messages WHERE sessionId = $sessionId ")
        }

        if (sender != null) {
            queryBuilder.append(" AND chat_messages.senderName = '${sender.replace("'", "''")}'")
        }
        if (dateStart != null) {
            queryBuilder.append(" AND chat_messages.timestamp >= $dateStart")
        }
        if (dateEnd != null) {
            queryBuilder.append(" AND chat_messages.timestamp <= $dateEnd")
        }
        if (type != null) {
            queryBuilder.append(" AND chat_messages.messageType = '$type'")
        }
        if (isBookmarked != null) {
            queryBuilder.append(" AND chat_messages.isBookmarked = ${if (isBookmarked) 1 else 0}")
        }
        if (useFts) {
            val ftsQuery = "${query?.replace("'", "''")}*"
            queryBuilder.append(" AND chat_messages_fts MATCH '$ftsQuery'")
        }
        queryBuilder.append(" ORDER BY chat_messages.timestamp ASC")

        return SimpleSQLiteQuery(queryBuilder.toString())
    }
}
