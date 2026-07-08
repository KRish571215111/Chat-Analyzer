package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.ai.AiService
import com.example.data.*
import com.example.parser.WhatsAppParser
import com.example.parser.ZipExtractor
import com.example.parser.ExtractionResult
import com.example.utils.EncryptionHelper
import com.example.parser.ImportedMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.security.MessageDigest

sealed interface Screen {
    object Onboarding : Screen
    object Home : Screen
    object Workspaces : Screen
    object TaskCenter : Screen
    data class ChatDashboard(val sessionId: Long) : Screen
    data class ParticipantProfile(val sessionId: Long, val participantId: Long) : Screen
    data class Participants(val sessionId: Long) : Screen
    data class MessageViewer(val sessionId: Long, val initialSenderName: String? = null) : Screen
    data class Bookmarks(val sessionId: Long) : Screen
    data class MediaGallery(val sessionId: Long) : Screen
    data class AiAssistant(val sessionId: Long) : Screen
    data class Analytics(val sessionId: Long) : Screen
    data class Reports(val sessionId: Long) : Screen
    data class MemberImport(val sessionId: Long) : Screen
    data class ParticipantComparison(val sessionId: Long) : Screen
    object Settings : Screen
    object ImportWizard : Screen
}

sealed interface ImportState {
    object Idle : ImportState
    data class Loading(
        val stage: String, // "detecting", "extracting", "searching", "reading", "parsing", "saving", "completed"
        val percentage: Float,
        val elapsedTime: String,
        val estimatedRemaining: String,
        val progressText: String
    ) : ImportState
    data class DuplicateDetected(
        val fileHash: String,
        val existingSessionId: Long,
        val existingSessionName: String,
        val fileUri: Uri,
        val chatName: String
    ) : ImportState
    data class Success(val sessionId: Long) : ImportState
    data class Error(val message: String) : ImportState
}

sealed interface AiState {
    object Idle : AiState
    object Loading : AiState
    data class Success(val response: String) : AiState
    data class Error(val message: String) : AiState
}

sealed interface ImageAnalysisState {
    object Idle : ImageAnalysisState
    object Loading : ImageAnalysisState
    data class Success(val caption: String) : ImageAnalysisState
    data class Error(val message: String) : ImageAnalysisState
}

sealed interface ReportExportState {
    object Idle : ReportExportState
    object Loading : ReportExportState
    data class Progress(val step: Int, val message: String) : ReportExportState
    data class Success(val uri: Uri) : ReportExportState
    data class Error(val message: String) : ReportExportState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database)
    val aiService = AiService()

    // Screen Navigation
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Onboarding)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val screenHistory = mutableListOf<Screen>()

    // All Chat Sessions
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Import State
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private var cachedExtractionResult: ExtractionResult? = null
    private var cachedImportedMembers: List<com.example.parser.ImportedMember>? = null

    init {
        // Interruption recovery: Clean up any sessions that failed mid-import (totalMessages == 0)
        viewModelScope.launch(Dispatchers.IO) {
            val all = repository.allSessions.first()
            for (session in all) {
                if (session.totalMessages == 0) {
                    repository.deleteSession(session.id)
                }
            }
        }
    }

    // Export State
    private val _exportState = MutableStateFlow<ReportExportState>(ReportExportState.Idle)
    val exportState: StateFlow<ReportExportState> = _exportState.asStateFlow()

    // AI summary State
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()
    
    private val _pendingAiPrompt = MutableStateFlow<String?>(null)
    val pendingAiPrompt: StateFlow<String?> = _pendingAiPrompt.asStateFlow()
    
    private val _pendingAiMedia = MutableStateFlow<com.example.data.MediaFile?>(null)
    val pendingAiMedia: StateFlow<com.example.data.MediaFile?> = _pendingAiMedia.asStateFlow()

    private val _aiConsentRemembered = MutableStateFlow(false)
    val aiConsentRemembered: StateFlow<Boolean> = _aiConsentRemembered.asStateFlow()

    fun updateAiConsentRemembered(remember: Boolean) {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        _aiConsentRemembered.value = remember
        if (remember) {
            prefs.edit().putBoolean("ai_consent", true).apply()
        } else {
            prefs.edit().remove("ai_consent").apply()
        }
    }
    
    fun requestAiConsent(prompt: String, mediaFile: com.example.data.MediaFile? = null) {
        if (_aiConsentRemembered.value) {
            if (mediaFile != null) {
                analyzeExtractedImage(mediaFile, prompt)
            } else {
                askAi(prompt)
            }
        } else {
            _pendingAiPrompt.value = prompt
            _pendingAiMedia.value = mediaFile
        }
    }

    fun confirmAiConsent(remember: Boolean) {
        if (remember) {
            updateAiConsentRemembered(true)
        }
        val prompt = _pendingAiPrompt.value
        val mediaFile = _pendingAiMedia.value
        if (prompt != null) {
            if (mediaFile != null) {
                analyzeExtractedImage(mediaFile, prompt)
            } else {
                askAi(prompt)
            }
        }
        _pendingAiPrompt.value = null
        _pendingAiMedia.value = null
    }

    fun cancelAiConsent() {
        _pendingAiPrompt.value = null
        _pendingAiMedia.value = null
    }

    // Image AI Analysis State
    private val _imageAnalysisState = MutableStateFlow<ImageAnalysisState>(ImageAnalysisState.Idle)
    val imageAnalysisState: StateFlow<ImageAnalysisState> = _imageAnalysisState.asStateFlow()

    // Theme Engine Configurations
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _amoledDark = MutableStateFlow(false)
    val amoledDark: StateFlow<Boolean> = _amoledDark.asStateFlow()

    private val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _secureStorage = MutableStateFlow(false)
    val secureStorage: StateFlow<Boolean> = _secureStorage.asStateFlow()

    // Selected session states
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    val currentSession: StateFlow<ChatSession?> = _selectedSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getSessionById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentParticipants: StateFlow<List<Participant>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getParticipants(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMessages: StateFlow<List<ChatMessage>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessages(id).map { list ->
                    val isEnc = _secureStorage.value
                    list.map { msg ->
                        msg.copy(messageText = EncryptionHelper.decrypt(msg.messageText, isEnc))
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMediaFiles: StateFlow<List<MediaFile>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getMediaFilesForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkedMessages: StateFlow<List<ChatMessage>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getBookmarkedMessages(id).map { list ->
                    val isEnc = _secureStorage.value
                    list.map { msg ->
                        msg.copy(messageText = EncryptionHelper.decrypt(msg.messageText, isEnc))
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Message viewer filters
    private val _filterSender = MutableStateFlow<String?>(null)
    val filterSender: StateFlow<String?> = _filterSender.asStateFlow()

    private val _filterType = MutableStateFlow<String?>(null)
    val filterType: StateFlow<String?> = _filterType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredMessagesPaged: Flow<androidx.paging.PagingData<ChatMessage>> = combine(
        _selectedSessionId, _filterSender, _filterType, _searchQuery, _secureStorage
    ) { sessionId: Long?, sender: String?, type: String?, query: String, isEnc: Boolean ->
        Triple(sessionId, Triple(sender, type, query), isEnc)
    }.flatMapLatest { (sessionId, filters, isEnc) ->
        val (sender, type, query) = filters
        if (sessionId != null) {
            repository.filterMessagesPaged(
                sessionId = sessionId,
                sender = sender,
                type = type,
                query = query
            ).map { pagingData ->
                pagingData.map { msg ->
                    msg.copy(messageText = EncryptionHelper.decrypt(msg.messageText, isEnc))
                }
            }
        } else {
            flowOf(androidx.paging.PagingData.empty())
        }
    }.cachedIn(viewModelScope)

    // Legacy un-paged version for dashboard stats if needed (but currently dashboard uses currentMessages which is not paginated)
    private val _filteredMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val filteredMessages: StateFlow<List<ChatMessage>> = _filteredMessages.asStateFlow()

    val recentSearches: StateFlow<List<SearchHistory>> = repository.getRecentSearches(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings
    private val _aiApiKeySetting = MutableStateFlow("YOUR_ZAI_API_KEY_HERE")
    val aiApiKeySetting: StateFlow<String> = _aiApiKeySetting.asStateFlow()

    private val _useZaiSetting = MutableStateFlow(false)
    val useZaiSetting: StateFlow<Boolean> = _useZaiSetting.asStateFlow()

    private val _zaiBaseUrlSetting = MutableStateFlow("https://api.z.ai/v1/")
    val zaiBaseUrlSetting: StateFlow<String> = _zaiBaseUrlSetting.asStateFlow()

    private val _zaiModelSetting = MutableStateFlow("glm-4.7-flash")
    val zaiModelSetting: StateFlow<String> = _zaiModelSetting.asStateFlow()

    init {
        // Load API key from local preferences if present
        val prefs = application.getSharedPreferences("group_analyzer_prefs", Application.MODE_PRIVATE)
        val apiKey = prefs.getString("zai_api_key", "YOUR_ZAI_API_KEY_HERE") ?: "YOUR_ZAI_API_KEY_HERE"
        val enableZai = prefs.getBoolean("enable_zai", false)
        val baseUrl = prefs.getString("zai_base_url", "https://api.z.ai/v1/") ?: "https://api.z.ai/v1/"
        val zaiModel = prefs.getString("zai_model", "glm-4.7-flash") ?: "glm-4.7-flash"
        
        val appPrefs = application.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        _aiConsentRemembered.value = appPrefs.getBoolean("ai_consent", false)

        _aiApiKeySetting.value = apiKey
        _useZaiSetting.value = enableZai
        _zaiBaseUrlSetting.value = baseUrl
        _zaiModelSetting.value = zaiModel
        aiService.updateSettings(apiKey, enableZai, baseUrl, zaiModel)

        // Load theme settings
        _themeMode.value = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        _amoledDark.value = prefs.getBoolean("amoled_dark", false)
        _highContrast.value = prefs.getBoolean("high_contrast", false)
        _fontScale.value = prefs.getFloat("font_scale", 1.0f)
        _secureStorage.value = prefs.getBoolean("secure_storage", false)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)
        if (onboardingComplete) {
            _currentScreen.value = Screen.Home
        }

        // Initialization complete
    }

    fun navigateTo(screen: Screen) {
        screenHistory.add(_currentScreen.value)
        _currentScreen.value = screen
        if (screen is Screen.ChatDashboard) {
            _selectedSessionId.value = screen.sessionId
        }
    }

    fun completeOnboarding() {
        val prefs = getApplication<Application>().getSharedPreferences("group_analyzer_prefs", android.app.Application.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        _currentScreen.value = Screen.Home
    }

    fun navigateBack(): Boolean {
        if (screenHistory.isNotEmpty()) {
            val prev = screenHistory.removeAt(screenHistory.size - 1)
            _currentScreen.value = prev
            if (prev is Screen.ChatDashboard) {
                _selectedSessionId.value = prev.sessionId
            } else if (prev is Screen.Home) {
                _selectedSessionId.value = null
            }
            return true
        }
        return false
    }

    fun updateSettings(apiKey: String, useZai: Boolean, baseUrl: String = "https://api.z.ai/v1/", model: String = "glm-4.7-flash") {
        _aiApiKeySetting.value = apiKey
        _useZaiSetting.value = useZai
        _zaiBaseUrlSetting.value = baseUrl
        _zaiModelSetting.value = model
        aiService.updateSettings(apiKey, useZai, baseUrl, model)

        val prefs = getApplication<Application>().getSharedPreferences("group_analyzer_prefs", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("zai_api_key", apiKey)
            .putBoolean("enable_zai", useZai)
            .putString("zai_base_url", baseUrl)
            .putString("zai_model", model)
            .apply()
    }

    fun updateThemeSettings(mode: String, amoled: Boolean, highContrast: Boolean, scale: Float) {
        _themeMode.value = mode
        _amoledDark.value = amoled
        _highContrast.value = highContrast
        _fontScale.value = scale

        val prefs = getApplication<Application>().getSharedPreferences("group_analyzer_prefs", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("theme_mode", mode)
            .putBoolean("amoled_dark", amoled)
            .putBoolean("high_contrast", highContrast)
            .putFloat("font_scale", scale)
            .apply()
    }

    fun updateSecureStorage(enabled: Boolean) {
        _secureStorage.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("group_analyzer_prefs", Application.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("secure_storage", enabled)
            .apply()
    }

    private fun isArchiveFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(6)
                val read = input.read(header)
                if (read >= 4) {
                    val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                    val is7z = read >= 6 && header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
                            header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte() &&
                            header[4] == 0x27.toByte() && header[5] == 0x1C.toByte()
                    isZip || is7z
                } else false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun computeSha256(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            val sb = java.lang.StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            "hash_" + text.length + "_" + System.currentTimeMillis()
        }
    }

    fun executeImportPipeline(context: Context, uris: List<Uri>, participantText: String, chatName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _importState.value = ImportState.Loading("validating", 0.05f, "00:00", "--:--", "Validating files...")
                
                var primaryUri: Uri? = null
                val participantUris = mutableListOf<Uri>()
                
                for (uri in uris) {
                    val name = com.example.ui.screens.getFileName(context, uri)
                    val role = com.example.ui.screens.determineRole(name, context, uri)
                    if (role == "WhatsApp Export" || role == "ZIP" || role == "Text File") {
                        if (primaryUri == null) primaryUri = uri
                    } else if (role == "Member List") {
                        participantUris.add(uri)
                    }
                }
                
                if (primaryUri == null) {
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.Error("No valid chat export or ZIP archive selected.")
                    }
                    return@launch
                }
                
                _importState.value = ImportState.Loading("parsing_members", 0.1f, "00:01", "--:--", "Reading participant files...")
                
                val allImportedMembers = mutableListOf<com.example.parser.ImportedMember>()
                
                for (pUri in participantUris) {
                    try {
                        val members = com.example.parser.MemberImportEngine.parseFile(context, pUri)
                        allImportedMembers.addAll(members)
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error parsing participant file", e)
                    }
                }
                
                if (participantText.isNotBlank()) {
                    // Try parsing as simple text lines if manual entry provided
                    val lines = participantText.lines()
                    for (line in lines) {
                        val parts = line.split(Regex("[,\\-]"))
                        if (parts.size >= 2) {
                            allImportedMembers.add(com.example.parser.ImportedMember(parts[0].trim(), parts[1].trim(), com.example.parser.MemberImportEngine.normalizePhone(parts[1].trim())))
                        } else if (line.isNotBlank()) {
                            allImportedMembers.add(com.example.parser.ImportedMember(line.trim(), null, null))
                        }
                    }
                }
                
                cachedImportedMembers = allImportedMembers
                
                // Now proceed with the primary import flow
                importChatFromUri(primaryUri, chatName)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(e.localizedMessage ?: "Validation error")
                }
            }
        }
    }

    fun importChatFromUri(uri: Uri, chatName: String) {
        val context = getApplication<Application>()
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val isZip = isArchiveFile(context, uri)
                val sessionIdStr = "session_" + System.currentTimeMillis()

                val extractionResult = if (isZip) {
                    ZipExtractor.extractZipArchive(context, uri, sessionIdStr) { progress ->
                        val elapsedSeconds = progress.elapsedTimeMs / 1000
                        val elapsedStr = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                        val remainingStr = if (progress.estimatedRemainingTimeMs >= 0) {
                            val remainingSeconds = progress.estimatedRemainingTimeMs / 1000
                            String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                        } else "--:--"

                        val progressText = when (progress.stage) {
                            "detecting" -> "Archive detected. Reading headers..."
                            "extracting" -> "Extracting archived assets... (${(progress.percentage * 100).toInt()}%)"
                            "searching" -> "Scanning folders recursively..."
                            else -> "Extraction completed!"
                        }

                        val overallProgress = progress.percentage * 0.4f

                        
                _importState.value = ImportState.Loading(
                            stage = progress.stage,
                            percentage = overallProgress,
                            elapsedTime = elapsedStr,
                            estimatedRemaining = remainingStr,
                            progressText = progressText
                        )
                    }
                } else {
                    
                _importState.value = ImportState.Loading("reading", 0.2f, "00:01", "--:--", "Reading chat text...")
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Could not open file URI")
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    val chatText = reader.readText()
                    reader.close()

                    val fileHash = computeSha256(chatText)

                    ExtractionResult(
                        chatFileUri = uri,
                        chatFilePath = uri.toString(),
                        chatFileText = chatText,
                        chatFileType = ZipExtractor.classifyChatText(chatText),
                        fileHash = fileHash,
                        mediaFiles = emptyList(),
                        tempDir = ""
                    )
                }

                if (extractionResult.chatFileType == "UNSUPPORTED") {
                    throw Exception("The file format is unsupported. Please upload a valid WhatsApp or Telegram chat export.")
                }

                // Check for duplicate session using file hash
                val duplicateSession = repository.getSessionByHashSync(extractionResult.fileHash)
                if (duplicateSession != null) {
                    withContext(Dispatchers.Main) {
                        _importState.value = ImportState.DuplicateDetected(
                            fileHash = extractionResult.fileHash,
                            existingSessionId = duplicateSession.id,
                            existingSessionName = duplicateSession.name,
                            fileUri = uri,
                            chatName = chatName
                        )
                    }
                    cachedExtractionResult = extractionResult
                    return@launch
                }

                proceedWithImport(chatName, extractionResult, java.util.UUID.randomUUID().toString())

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Import failed", e)
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun proceedWithImport(chatName: String, extraction: ExtractionResult, taskId: String) {
        val isEncrypted = _secureStorage.value
        
        com.example.engine.TaskManager.submitTask(com.example.engine.BackgroundTask(id = taskId, name = "Import Chat: $chatName", type = com.example.engine.TaskType.IMPORT))

        com.example.engine.TaskManager.updateTaskProgress(taskId, 0.6f, "Parsing...")
        _importState.value = ImportState.Loading("parsing", 0.6f, "00:02", "--:--", "Parsing messages & participants...")
        val lines = extraction.chatFileText.lines()
        
        var sessionId = -1L
        try {
            val originalFileName = extraction.chatFileUri?.lastPathSegment ?: chatName
            // Try to extract original size from chatFileText byte array length if ZipExtractor didn't provide it 
            // but we can just use the text byte size for simplicity
            val originalFileSize = extraction.chatFileText.toByteArray(Charsets.UTF_8).size.toLong()

            sessionId = repository.createSession(
                name = chatName, 
                filePath = extraction.chatFilePath ?: extraction.chatFileUri.toString(),
                fileHash = extraction.fileHash,
                originalFileName = originalFileName,
                originalFileSize = originalFileSize,
                textEncoding = "UTF-8"
            )

            val startTime = System.currentTimeMillis()
            val parseResult = WhatsAppParser.parseChatExport(lines, sessionId) { progress, parsedCount, totalLines ->
                val elapsedSecs = (System.currentTimeMillis() - startTime) / 1000
                val elapsedStr = String.format("%02d:%02d", elapsedSecs / 60, elapsedSecs % 60)
                val estRemaining = if (progress > 0) ((elapsedSecs / progress) - elapsedSecs).toLong() else -1L
                val remainingStr = if (estRemaining >= 0) String.format("%02d:%02d", estRemaining / 60, estRemaining % 60) else "--:--"
                
                // map progress from 0.0-1.0 to 0.4-0.8 for the UI
                val adjustedProgress = 0.4f + (progress * 0.4f)
                
                _importState.value = ImportState.Loading(
                    stage = "parsing",
                    percentage = adjustedProgress,
                    elapsedTime = elapsedStr,
                    estimatedRemaining = remainingStr,
                    progressText = "Parsing: $parsedCount messages found..."
                )
            }
            if (parseResult.totalMessages == 0) {
                throw Exception("Could not find any valid WhatsApp messages. Unsupported format.")
            }

            // Move participant matching here so we can rename message senders BEFORE saving
            val membersFromZip = mutableListOf<com.example.parser.ImportedMember>()
            val roomMedia = extraction.mediaFiles.mapNotNull { media ->
                if (media.fileType == "MEMBER_LIST") {
                    try {
                        val fileUri = android.net.Uri.fromFile(java.io.File(media.localPath))
                        val parsed = com.example.parser.MemberImportEngine.parseFile(getApplication<Application>(), fileUri)
                        membersFromZip.addAll(parsed)
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to parse member list from ZIP: ${media.fileName}", e)
                    }
                    null
                } else {
                    media
                }
            }

            val allMembers = (cachedImportedMembers ?: emptyList()) + membersFromZip
            val (finalParticipants, stats) = com.example.engine.ParticipantMatchingEngine.matchParticipants(
                sessionId,
                parseResult.participants,
                allMembers,
                aiService
            )
            repository.insertParticipants(finalParticipants)
            cachedImportedMembers = null

            val processedMessages = if (isEncrypted) {
                parseResult.messages.map { msg ->
                    val newSender = stats.oldToNewNameMap[msg.senderName] ?: msg.senderName
                    msg.copy(
                        senderName = newSender,
                        messageText = EncryptionHelper.encrypt(msg.messageText, true),
                        rawMessageText = EncryptionHelper.encrypt(msg.rawMessageText, true)
                    )
                }
            } else {
                parseResult.messages.map { msg ->
                    val newSender = stats.oldToNewNameMap[msg.senderName] ?: msg.senderName
                    if (newSender != msg.senderName) msg.copy(senderName = newSender) else msg
                }
            }

            com.example.engine.TaskManager.updateTaskProgress(taskId, 0.8f, "Saving to SQLite database...")
            _importState.value = ImportState.Loading("saving", 0.8f, "00:03", "--:--", "Saving to SQLite database...")
            repository.insertMessagesInBatches(processedMessages) { saveProgress -> 
                val adjustedProgress = 0.8f + (saveProgress * 0.1f) // map 0.0-1.0 to 0.8-0.9
                com.example.engine.TaskManager.updateTaskProgress(taskId, adjustedProgress, "Saving messages to database...")
                _importState.value = ImportState.Loading(
                    stage = "saving",
                    percentage = adjustedProgress,
                    elapsedTime = "00:03",
                    estimatedRemaining = "--:--",
                    progressText = "Saving messages to database... ${(saveProgress * 100).toInt()}%"
                )
            }

            val finalRoomMedia = roomMedia.map { media ->
                val sender = getSenderForMedia(media.fileName, processedMessages, media.timestamp)
                MediaFile(
                    sessionId = sessionId,
                    fileName = media.fileName,
                    filePath = media.localPath,
                    fileSize = media.fileSize,
                    fileType = media.fileType,
                    senderName = sender,
                    timestamp = media.timestamp
                )
            }

            if (finalRoomMedia.isNotEmpty()) {
                repository.insertMediaFiles(finalRoomMedia)
            }

            repository.updateSessionStats(
                id = sessionId,
                totalMessages = parseResult.totalMessages,
                dateStart = parseResult.dateStart,
                dateEnd = parseResult.dateEnd
            )

            val finalParticipantCount = repository.getParticipantsSync(sessionId).size

            // Release blocker requirement: Verify database integrity before analytics generation.
            val dbMessagesCount = repository.getMessagesCountSync(sessionId)
            if (dbMessagesCount != parseResult.totalMessages) {
                throw Exception("Database integrity validation failed: Messages parsed (${parseResult.totalMessages}) does not match messages saved ($dbMessagesCount).")
            }

            val finalMediaCount = repository.getMediaCountSync(sessionId)
            if (finalMediaCount != finalRoomMedia.size) {
                throw Exception("Database integrity validation failed: Media extracted (${finalRoomMedia.size}) does not match media saved ($finalMediaCount).")
            }

            repository.updateSessionMetadata(
                id = sessionId,
                participantCount = finalParticipantCount,
                mediaFileCount = finalRoomMedia.size,
                extractedFileCount = extraction.mediaFiles.size + 1 // +1 for the chat txt itself
            )

            com.example.engine.TaskManager.completeTask(taskId, true)
            _importState.value = ImportState.Loading("completed", 1.0f, "00:04", "00:00", "Analysis completed!")
            delay(800)

            withContext(Dispatchers.Main) {
                _selectedSessionId.value = sessionId
                _importState.value = ImportState.Success(sessionId)
                navigateTo(Screen.ChatDashboard(sessionId))
            }
        } catch (e: Exception) {
            if (sessionId != -1L) {
                repository.deleteSession(sessionId)
            }
            if (extraction.tempDir.isNotBlank()) {
                val tempDir = File(extraction.tempDir)
                if (tempDir.exists()) tempDir.deleteRecursively()
            }
            throw e
        }
    }

    fun confirmImportReplace(existingSessionId: Long, uri: Uri, chatName: String) {
        val extraction = cachedExtractionResult ?: return
        
                _importState.value = ImportState.Loading("saving", 0.1f, "00:00", "--:--", "Deleting old duplicate analysis...")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                repository.deleteSession(existingSessionId)
                repository.deleteMediaFilesForSession(existingSessionId)
                proceedWithImport(chatName, extraction, java.util.UUID.randomUUID().toString())
                cachedExtractionResult = null
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(e.localizedMessage ?: "Replace duplicate failed")
                }
            }
        }
    }

    fun confirmImportKeepBoth(uri: Uri, chatName: String) {
        val extraction = cachedExtractionResult ?: return
        
                _importState.value = ImportState.Loading("saving", 0.1f, "00:00", "--:--", "Creating copy...")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                proceedWithImport("$chatName (Copy)", extraction, java.util.UUID.randomUUID().toString())
                cachedExtractionResult = null
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(e.localizedMessage ?: "Keep-both duplicate failed")
                }
            }
        }
    }

    private fun getSenderForMedia(fileName: String, messages: List<ChatMessage>, timestamp: Long): String {
        val cleanName = fileName.substringBeforeLast(".")
        val exactMatch = messages.firstOrNull { msg ->
            msg.isMedia && msg.messageText.contains(cleanName, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch.senderName

        val windowMs = 2 * 60 * 1000
        val timeMatch = messages.firstOrNull { msg ->
            msg.isMedia && Math.abs(msg.timestamp - timestamp) < windowMs
        }
        return timeMatch?.senderName ?: "Unknown"
    }

    private data class SyntheticMsgSpec(
        val text: String,
        val isMedia: Boolean,
        val isSystem: Boolean,
        val isDeleted: Boolean,
        val msgType: String
    )

    fun importEnterpriseSyntheticDataset() {
        val context = getApplication<Application>()
        _importState.value = ImportState.Loading("detecting", 0.1f, "00:00", "--:--", "Starting Enterprise QA Dataset Generation...")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val isEncrypted = _secureStorage.value
                val taskId = java.util.UUID.randomUUID().toString()
                com.example.engine.TaskManager.submitTask(com.example.engine.BackgroundTask(id = taskId, name = "Synthetic Dataset Gen", type = com.example.engine.TaskType.IMPORT))

                val sessionName = "Enterprise QA Validation Workspace"
                val fileHash = "synthetic_qa_dataset_hash_10k_" + System.currentTimeMillis()

                _importState.value = ImportState.Loading("saving", 0.2f, "00:01", "--:--", "Creating database workspace...")
                val sessionId = repository.createSession(
                    name = sessionName,
                    filePath = "synthetic://enterprise_qa",
                    fileHash = fileHash,
                    originalFileName = "enterprise_synthetic_dataset_10k.txt",
                    originalFileSize = 1048576L, // 1MB size representation
                    textEncoding = "UTF-8"
                )

                // 1. Generate 105 participants
                _importState.value = ImportState.Loading("saving", 0.4f, "00:02", "--:--", "Generating 105 highly diverse participants...")
                val participants = mutableListOf<Participant>()
                val participantNames = (1..105).map { idx ->
                    when {
                        idx == 1 -> "Krish"
                        idx == 2 -> "Alice Smith"
                        idx == 3 -> "Bob Johnson"
                        idx == 4 -> "Charlie Brown"
                        idx == 5 -> "David Lee"
                        idx <= 10 -> "Participant $idx"
                        idx <= 30 -> "+91 98765${10000 + idx}" // Phone sender
                        idx <= 50 -> "Sender $idx"
                        idx <= 100 -> "Group Member $idx"
                        else -> "Silent Observer ${idx - 100}" // Silent members (no messages)
                    }
                }

                val startTime = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000 // 60 days ago
                val endTime = System.currentTimeMillis()

                for ((index, name) in participantNames.withIndex()) {
                    val isSilent = index >= 100
                    val isPhone = name.startsWith("+")
                    val phoneStr = if (isPhone) name else null
                    val normalizedPhone = if (isPhone) com.example.parser.MemberImportEngine.normalizePhone(name) else null
                    
                    val msgCount = if (isSilent) 0 else (1000 - index * 8).coerceAtLeast(12)
                    val wordCount = msgCount * 6
                    val charCount = wordCount * 5
                    val mediaCount = (msgCount * 0.1).toInt().coerceAtLeast(0)
                    val emojiCount = (msgCount * 0.3).toInt()
                    val linkCount = (msgCount * 0.05).toInt()

                    participants.add(
                        Participant(
                            sessionId = sessionId,
                            name = name,
                            phone = phoneStr,
                            normalizedPhone = normalizedPhone,
                            messageCount = msgCount,
                            wordCount = wordCount,
                            characterCount = charCount,
                            mediaCount = mediaCount,
                            emojiCount = emojiCount,
                            linkCount = linkCount,
                            imagesShared = (mediaCount * 0.4).toInt(),
                            videosShared = (mediaCount * 0.2).toInt(),
                            documentsShared = (mediaCount * 0.1).toInt(),
                            voiceNotesShared = (mediaCount * 0.2).toInt(),
                            audioShared = (mediaCount * 0.1).toInt(),
                            favoriteEmoji = if (index % 5 == 0) "😊" else if (index % 5 == 1) "😂" else if (index % 5 == 2) "🚀" else if (index % 5 == 3) "🔥" else "👍",
                            mostUsedWord = if (index % 3 == 0) "build" else if (index % 3 == 1) "design" else "app",
                            sentimentScore = 0.4f + (index % 5) * 0.1f - 0.2f, // Mixed sentiments
                            activityScore = msgCount / 10f,
                            contributionPercentage = msgCount / 100f,
                            firstActive = startTime + index * 1000L * 60 * 60,
                            lastActive = endTime - index * 1000L * 60 * 60,
                            isFavorite = index < 5
                        )
                    )
                }

                repository.insertParticipants(participants)

                // 2. Generate 10,200 messages in batch
                _importState.value = ImportState.Loading("saving", 0.6f, "00:03", "--:--", "Generating 10,200 realistic messages...")
                val sampleTexts = listOf(
                    "Hello group! Let's check the design today.",
                    "The linter completed successfully, let's deploy.",
                    "Are we using Room for database persistence?",
                    "Checkout the SQLite specification here: https://sqlite.org",
                    "We need to implement proper phone normalization as per QA instructions.",
                    "Please avoid hardcoding API keys in the app. Use .env and BuildConfig.",
                    "Yes, let's keep edge-to-edge support enabled everywhere.",
                    "Material 3 is beautiful and clean.",
                    "Can we build a test suite to validate all 5 file formats?",
                    "Perfect! Let's verify and test.",
                    "Is anyone working on the HTML report generation?",
                    "I will work on the ZIP compression engine today.",
                    "Don't forget to implement custom adaptive launcher icons.",
                    "Amazing, this looks highly polished!",
                    "Who created the new workspace?",
                    "Please upload the XLSX template for member list parsing."
                )

                val emojis = listOf("😊", "😂", "🚀", "🔥", "👍", "💡", "🙌", "🎉", "👏", "⚡")

                val totalMessagesCount = 10200
                val processedMessages = mutableListOf<ChatMessage>()
                val random = java.util.Random(42)

                for (i in 1..totalMessagesCount) {
                    val pIdx = random.nextInt(100) // Select from active members
                    val sender = participantNames[pIdx]
                    val daysOffset = random.nextInt(60)
                    val hour = 7 + random.nextInt(16) // Mostly active between 7 AM and 11 PM
                    val minute = random.nextInt(60)
                    val second = random.nextInt(60)
                    
                    val timestamp = startTime + (daysOffset * 24L * 60 * 60 * 1000) + (hour * 60L * 60 * 1000) + (minute * 60L * 1000) + (second * 1000)

                    val textTypeIdx = random.nextInt(100)
                    val spec = when {
                        textTypeIdx < 85 -> {
                            val base = sampleTexts[random.nextInt(sampleTexts.size)]
                            val emoji = if (random.nextBoolean()) " " + emojis[random.nextInt(emojis.size)] else ""
                            val fullText = base + emoji
                            val isLink = fullText.contains("http")
                            val mType = if (isLink) "LINK" else "TEXT"
                            SyntheticMsgSpec(fullText, false, false, false, mType)
                        }
                        textTypeIdx < 90 -> SyntheticMsgSpec("<image omitted>", true, false, false, "IMAGE")
                        textTypeIdx < 92 -> SyntheticMsgSpec("<video omitted>", true, false, false, "VIDEO")
                        textTypeIdx < 94 -> SyntheticMsgSpec("voice note (file attached)", true, false, false, "VOICE_NOTE")
                        textTypeIdx < 96 -> SyntheticMsgSpec("document (file attached)", true, false, false, "DOCUMENT")
                        textTypeIdx < 97 -> SyntheticMsgSpec("This message was deleted", false, false, true, "DELETED")
                        textTypeIdx < 98 -> SyntheticMsgSpec("System event: ${sender} changed the group description", false, true, false, "SYSTEM")
                        else -> SyntheticMsgSpec("POLL: Best architecture? Options: MVVM, Clean, MVI", false, false, false, "POLL")
                    }

                    val finalMessageText = if (isEncrypted) EncryptionHelper.encrypt(spec.text, true) else spec.text
                    val finalRawText = if (isEncrypted) EncryptionHelper.encrypt(spec.text, true) else spec.text

                    processedMessages.add(
                        ChatMessage(
                            sessionId = sessionId,
                            senderName = if (spec.isSystem) "System" else sender,
                            timestamp = timestamp,
                            messageText = finalMessageText,
                            rawMessageText = finalRawText,
                            messageType = spec.msgType,
                            isMedia = spec.isMedia,
                            isDeleted = spec.isDeleted,
                            isSystem = spec.isSystem,
                            isBookmarked = (i % 250 == 0),
                            isPinned = (i % 500 == 0)
                        )
                    )
                }

                // Sort messages chronologically by timestamp
                processedMessages.sortBy { it.timestamp }

                _importState.value = ImportState.Loading("saving", 0.8f, "00:04", "--:--", "Inserting 10,200 messages in DB batches...")
                repository.insertMessagesInBatches(processedMessages) { progress ->
                    // UI progress feedback
                    val adjustedProgress = 0.8f + (progress * 0.1f)
                    _importState.value = ImportState.Loading(
                        stage = "saving",
                        percentage = adjustedProgress,
                        elapsedTime = "00:04",
                        estimatedRemaining = "--:--",
                        progressText = "Bulk inserting synthetic messages... ${(progress * 100).toInt()}%"
                    )
                }

                // 3. Generate 1,010 Media files
                _importState.value = ImportState.Loading("saving", 0.9f, "00:05", "--:--", "Generating 1,010 media registry index files...")
                val roomMedia = mutableListOf<MediaFile>()
                val mediaExtensions = mapOf("IMAGE" to "jpg", "VIDEO" to "mp4", "AUDIO" to "mp3", "VOICE_NOTE" to "ogg", "DOCUMENT" to "pdf")
                
                var mediaCount = 0
                for (msg in processedMessages) {
                    if (msg.isMedia && mediaCount < 1010) {
                        val ext = mediaExtensions[msg.messageType] ?: "bin"
                        val fileName = "FILE_${mediaCount}_${msg.timestamp}.$ext"
                        roomMedia.add(
                            MediaFile(
                                sessionId = sessionId,
                                fileName = fileName,
                                filePath = context.cacheDir.absolutePath + "/$fileName",
                                fileSize = (1024L * (random.nextInt(4000) + 128)), // 128KB to 4MB
                                fileType = msg.messageType,
                                senderName = msg.senderName,
                                timestamp = msg.timestamp
                            )
                        )
                        mediaCount++
                    }
                }
                if (roomMedia.isNotEmpty()) {
                    repository.insertMediaFiles(roomMedia)
                }

                // 4. Generate AI summaries for testing AI insights
                repository.insertSummary(
                    AiSummary(
                        sessionId = sessionId,
                        category = "DECISIONS",
                        summaryText = "1. Resolved that Clean Architecture / MVVM combined with Jetpack Compose and Room database is the optimal baseline choice for developer efficiency and performance.\n2. Normalization of phone numbers will follow a global suffix-based schema (matching the last 10 digits) to deduplicate contacts reliably.\n3. Native HTML reports must bundle zero-dependency Inter fonts and inline CSS/JS rules for 100% standalone offline reliability."
                    )
                )
                repository.insertSummary(
                    AiSummary(
                        sessionId = sessionId,
                        category = "PERSONALITY",
                        summaryText = "The team is exceptionally collaborative and active during the evening hours (7 PM to 10 PM), with Krish leading administrative coordination and Alice spearheading design system discussions. Visual communication (using images and emojis) represents roughly 12% of overall engagement."
                    )
                )

                // Update session totals
                repository.updateSessionStats(
                    id = sessionId,
                    totalMessages = processedMessages.size,
                    dateStart = processedMessages.first().timestamp,
                    dateEnd = processedMessages.last().timestamp
                )

                repository.updateSessionMetadata(
                    id = sessionId,
                    participantCount = participants.size,
                    mediaFileCount = roomMedia.size,
                    extractedFileCount = roomMedia.size + 1
                )

                com.example.engine.TaskManager.completeTask(taskId, true)
                _importState.value = ImportState.Loading("completed", 1.0f, "00:05", "00:00", "Enterprise QA dataset loaded successfully!")
                delay(800)

                withContext(Dispatchers.Main) {
                    _selectedSessionId.value = sessionId
                    _importState.value = ImportState.Success(sessionId)
                    navigateTo(Screen.ChatDashboard(sessionId))
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Synthetic import failed", e)
                withContext(Dispatchers.Main) {
                    _importState.value = ImportState.Error(e.localizedMessage ?: "Synthetic generator failed")
                }
            }
        }
    }

    fun importSampleChat() {
        
                _importState.value = ImportState.Loading("detecting", 0.1f, "00:00", "--:--", "Generating sample workspace...")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sampleChat = """
                    25/06/2026, 09:30 - System: Krish created group "Flutter & Jetpack Devs"
                    25/06/2026, 09:31 - Krish: Hello everyone! Welcome to our group chat. Let's talk about building some beautiful Android apps here! 😊
                    25/06/2026, 09:33 - Alice: Hey Krish! This is awesome. I'm excited to explore custom layouts and Room databases!
                    25/06/2026, 09:35 - Bob: Awesome, count me in. Check out this article on Material 3 design: https://m3.material.io
                    25/06/2026, 09:40 - Alice: <Media omitted>
                    25/06/2026, 09:42 - Krish: That link is amazing, Bob! Emojis are fully supported too! Let's do some testing on larger files. 😂🚀
                    26/06/2026, 11:15 - Bob: Hey, did we decide on the SQLite library?
                    26/06/2026, 11:16 - Alice: I think we should use Room for Native and Drift for Flutter!
                    26/06/2026, 11:20 - Krish: Room is definitely the way to go for Native Kotlin!
                    26/06/2026, 11:21 - System: Alice added Charlie
                    26/06/2026, 11:25 - Charlie: Hello everyone! Glad to be here. Did I miss the design discussion?
                    26/06/2026, 11:26 - Krish: Charlie, check Bob's link above.
                    27/06/2026, 14:02 - Alice: <Media omitted>
                    27/06/2026, 14:05 - Charlie: Beautiful screenshots! Let's get to work.
                    27/06/2026, 14:10 - Bob: This message was deleted
                    28/06/2026, 10:00 - Krish: Meeting decisions: We will complete the parser by tomorrow.
                    28/06/2026, 10:05 - Alice: Sounds perfect, I'll work on the UI structure.
                    29/06/2026, 15:45 - Charlie: Missed voice call
                """.trimIndent().lines()

                val fileHash = computeSha256(sampleChat.joinToString("\n"))

                // Verify and proceed
                val extractionResult = ExtractionResult(
                    chatFileUri = Uri.parse("sample://internal"),
                    chatFilePath = "sample://internal",
                    chatFileText = sampleChat.joinToString("\n"),
                    chatFileType = "WHATSAPP",
                    fileHash = fileHash,
                    mediaFiles = emptyList(),
                    tempDir = ""
                )

                val duplicateSession = repository.getSessionByHashSync(fileHash)
                if (duplicateSession != null) {
                    withContext(Dispatchers.Main) {
                        _selectedSessionId.value = duplicateSession.id
                        _importState.value = ImportState.Success(duplicateSession.id)
                        navigateTo(Screen.ChatDashboard(duplicateSession.id))
                    }
                    return@launch
                }

                proceedWithImport("Dev Project Chat", extractionResult, java.util.UUID.randomUUID().toString())
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.localizedMessage ?: "Unknown sample error")
            }
        }
    }

    fun analyzeExtractedImage(mediaFile: MediaFile, prompt: String) {
        _imageAnalysisState.value = ImageAnalysisState.Loading
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val file = File(mediaFile.filePath)
                if (!file.exists()) {
                    throw FileNotFoundException("Physical image file has been cleared or deleted.")
                }
                val imageBytes = file.readBytes()
                val extension = file.extension.lowercase()
                val mimeType = when (extension) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }

                val aiResult = aiService.analyzeImage(prompt, imageBytes, mimeType)

                // Update database
                val updatedMedia = mediaFile.copy(
                    aiCaption = aiResult,
                    isScreenshot = aiResult.contains("screenshot", ignoreCase = true) || prompt.contains("screenshot", ignoreCase = true),
                    isMeme = aiResult.contains("meme", ignoreCase = true) || prompt.contains("meme", ignoreCase = true),
                    isDocument = aiResult.contains("document", ignoreCase = true) || aiResult.contains("receipt", ignoreCase = true) || prompt.contains("document", ignoreCase = true),
                    isChart = aiResult.contains("chart", ignoreCase = true) || aiResult.contains("graph", ignoreCase = true) || prompt.contains("chart", ignoreCase = true),
                    isQrCode = aiResult.contains("qr", ignoreCase = true) || prompt.contains("qr", ignoreCase = true)
                )
                repository.updateMediaFile(updatedMedia)

                withContext(Dispatchers.Main) {
                    _imageAnalysisState.value = ImageAnalysisState.Success(aiResult)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Image analysis failed", e)
                withContext(Dispatchers.Main) {
                    _imageAnalysisState.value = ImageAnalysisState.Error(e.localizedMessage ?: "Vision analysis error")
                }
            }
        }
    }

    fun clearImageAnalysisState() {
        _imageAnalysisState.value = ImageAnalysisState.Idle
    }

    fun toggleBookmark(messageId: Long, isBookmarked: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmark(messageId, isBookmarked)
        }
    }

    fun toggleFavoriteMessage(messageId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePin(messageId, isPinned)
        }
    }

    fun togglePin(messageId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePin(messageId, isPinned)
        }
    }

    fun getParticipantsForSession(sessionId: Long) = repository.getParticipants(sessionId)
    
    suspend fun mergeImportedMembers(sessionId: Long, importedMembers: List<ImportedMember>) {
        val currentParticipants = repository.getParticipantsSync(sessionId)
        val participantPhoneMap = currentParticipants.associateBy { it.normalizedPhone ?: "" }.filterKeys { it.isNotEmpty() }
        val participantNameMap = currentParticipants.associateBy { it.name }
        
        val toUpdate = mutableListOf<Participant>()
        val toInsert = mutableListOf<Participant>()
        
        val seenPhones = mutableSetOf<String>()
        val uniqueImported = mutableListOf<ImportedMember>()
        for (m in importedMembers) {
            if (m.normalizedPhone != null) {
                if (seenPhones.add(m.normalizedPhone)) uniqueImported.add(m)
            } else {
                uniqueImported.add(m)
            }
        }
        
        val seenInsertedNames = mutableSetOf<String>()
        val seenInsertedPhones = mutableSetOf<String>()

        for (imported in uniqueImported) {
            val matched = imported.normalizedPhone?.let { participantPhoneMap[it] } ?: participantNameMap[imported.originalName]
            if (matched != null) {
                // Update if imported provides a better name or phone
                val newName = if (imported.originalName.isNotBlank() && matched.name.matches(Regex("^[+\\d\\s\\-()]+$")) && !imported.originalName.matches(Regex("^[+\\d\\s\\-()]+$"))) {
                    imported.originalName
                } else {
                    matched.name
                }
                
                val updated = matched.copy(
                    name = newName,
                    phone = imported.originalPhone ?: matched.phone,
                    normalizedPhone = imported.normalizedPhone ?: matched.normalizedPhone
                )
                if (updated != matched) {
                    toUpdate.add(updated)
                }
            } else {
                val candidateName = imported.originalName.takeIf { it.isNotBlank() } ?: imported.originalPhone ?: "Unknown"
                val isDuplicate = (imported.normalizedPhone != null && seenInsertedPhones.contains(imported.normalizedPhone)) || 
                                  (imported.normalizedPhone == null && seenInsertedNames.contains(candidateName))

                if (!isDuplicate) {
                    // Silent member
                    toInsert.add(
                        Participant(
                            sessionId = sessionId,
                            name = candidateName,
                            phone = imported.originalPhone,
                            normalizedPhone = imported.normalizedPhone,
                            messageCount = 0
                        )
                    )
                    seenInsertedNames.add(candidateName)
                    if (imported.normalizedPhone != null) seenInsertedPhones.add(imported.normalizedPhone)
                }
            }
        }
        
        // In a real app we might batch update/insert, here we do it individually for update
        toUpdate.associateBy { it.id }.values.forEach { repository.updateParticipant(it) }
        if (toInsert.isNotEmpty()) {
            repository.insertParticipants(toInsert)
        }
    }

    fun toggleFavoriteParticipant(participantId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavoriteParticipant(participantId, isFavorite)
        }
    }

    fun setFilterSender(sender: String?) {
        _filterSender.value = sender
    }

    fun setFilterType(type: String?) {
        _filterType.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                repository.insertSearch(query)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
        cachedExtractionResult = null
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            repository.deleteMediaFilesForSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
                navigateTo(Screen.Home)
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = repository.allSessions.first()
            sessions.forEach {
                repository.deleteSession(it.id)
                repository.deleteMediaFilesForSession(it.id)
            }
            withContext(Dispatchers.Main) {
                _selectedSessionId.value = null
                navigateTo(Screen.Home)
            }
        }
    }

    fun deleteAllAiHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllSummaries()
        }
    }

    fun askAi(prompt: String) {
        val sessionId = _selectedSessionId.value ?: return
        _aiState.value = AiState.Loading
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val messages = repository.getMessagesSync(sessionId)
                val response = aiService.generateAnalysis(prompt, messages)

                val category = when {
                    prompt.contains("decision", true) -> "DECISIONS"
                    prompt.contains("personality", true) -> "PERSONALITY"
                    prompt.contains("health", true) -> "FAIRNESS"
                    else -> "GENERAL"
                }
                repository.insertSummary(
                    AiSummary(
                        sessionId = sessionId,
                        category = category,
                        summaryText = response
                    )
                )

                _aiState.value = AiState.Success(response)
            } catch (e: Exception) {
                _aiState.value = AiState.Error(e.localizedMessage ?: "AI Service Error")
            }
        }
    }

    fun clearAiState() {
        _aiState.value = AiState.Idle
    }

    fun exportInteractiveReport(context: Context, destUri: Uri) {
        val taskId = java.util.UUID.randomUUID().toString()
        com.example.engine.TaskManager.submitTask(com.example.engine.BackgroundTask(id = taskId, name = "Export Interactive Report", type = com.example.engine.TaskType.EXPORT))
        val sessionId = _selectedSessionId.value ?: return
        _exportState.value = ReportExportState.Loading
        
        viewModelScope.launch {
            try {
                val session = repository.getSessionByIdSync(sessionId) ?: throw Exception("Session not found")
                val participants = repository.getParticipantsSync(sessionId)
                val rawMessages = repository.getMessagesSync(sessionId)
                
                // FORCE DECRYPT all messages before export to guarantee no [ENC] escapes.
                val messages = rawMessages.map { msg ->
                    msg.copy(
                        messageText = EncryptionHelper.decrypt(msg.messageText, true),
                        rawMessageText = EncryptionHelper.decrypt(msg.rawMessageText, true)
                    )
                }
                
                val mediaFiles = repository.getMediaFilesForSessionSync(sessionId)
                val aiSummaries = repository.getSummariesSync(sessionId)
                
                val zipFile = com.example.utils.ReportGenerator.generateHtmlReportZip(
                    context, session, participants, messages, mediaFiles, aiSummaries
                ) { step, message ->
                    _exportState.value = ReportExportState.Progress(step, message)
                    com.example.engine.TaskManager.updateTaskProgress(taskId, step / 100f, message)
                }
                
                if (zipFile != null && zipFile.exists()) {
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        zipFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    zipFile.delete()
                    _exportState.value = ReportExportState.Success(destUri)
                    com.example.engine.TaskManager.completeTask(taskId, true)
                } else {
                    throw Exception("Failed to generate report ZIP")
                }
            } catch (e: Exception) {
                _exportState.value = ReportExportState.Error(e.localizedMessage ?: "Export failed")
                com.example.engine.TaskManager.completeTask(taskId, false, e.localizedMessage)
            }
        }
    }
    
    fun clearExportState() {
        _exportState.value = ReportExportState.Idle
    }
}
