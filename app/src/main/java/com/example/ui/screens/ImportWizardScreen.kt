package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.ChatViewModel
import com.example.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(1) }
    
    // State for Step 1
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedFiles = (selectedFiles + uris).take(5)
        }
    }
    
    // State for Step 2
    var participantText by remember { mutableStateOf("") }
    
    val hasChat = remember(selectedFiles) {
        selectedFiles.any { 
            val role = determineRole(getFileName(context, it), context, it)
            role == "WhatsApp Export" || role == "ZIP" || role == "Text File"
        }
    }

    val hasParticipantList = remember(selectedFiles) {
        selectedFiles.any { 
            determineRole(getFileName(context, it), context, it) == "Member List" 
        }
    }

    val importState by viewModel.importState.collectAsState()
    var isValidating by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Wizard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (currentStep > 1) {
                            if (currentStep == 3 && hasParticipantList) {
                                currentStep = 1
                            } else {
                                currentStep--
                            }
                        } else {
                            viewModel.navigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                actions = {
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentStep < 3) {
                        Button(
                            onClick = { 
                                if (currentStep == 1 && hasParticipantList) {
                                    currentStep = 3
                                } else {
                                    currentStep++ 
                                }
                            },
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = if (currentStep == 1) hasChat else true
                        ) {
                            Text(if (currentStep == 1 && hasParticipantList) "Skip to Summary" else "Next")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Button(
                            onClick = { 
                                if (selectedFiles.isNotEmpty()) {
                                    viewModel.executeImportPipeline(context, selectedFiles, participantText, "WhatsApp Chat Export")
                                }
                            },
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = hasChat && !isValidating && (importState is com.example.ui.ImportState.Idle || importState is com.example.ui.ImportState.Error)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Import")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Steps indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicator(step = 1, currentStep = currentStep, label = "Files")
                HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                StepIndicator(step = 2, currentStep = currentStep, label = "Participants")
                HorizontalDivider(modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                StepIndicator(step = 3, currentStep = currentStep, label = "Summary")
            }
            
            AnimatedContent(targetState = currentStep, label = "wizard_steps") { step ->
                when (step) {
                    1 -> Step1Files(selectedFiles, onSelectFiles = { filePickerLauncher.launch(arrayOf("*/*")) })
                    2 -> Step2Participants(participantText) { participantText = it }
                    3 -> Step3Summary(selectedFiles, participantText, importState, isValidating) { isValidating = it }
                }
            }
        }
    }
}

@Composable
fun StepIndicator(step: Int, currentStep: Int, label: String) {
    val isCompleted = step < currentStep
    val isActive = step == currentStep
    val color = if (isActive || isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isActive || isCompleted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(Icons.Default.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            } else {
                Text(step.toString(), color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun Step1Files(selectedFiles: List<Uri>, onSelectFiles: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select WhatsApp Export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Allow selecting up to 5 files simultaneously. Supported files: ZIP, TXT, CSV, VCF, JSON, XLSX, PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onSelectFiles,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = selectedFiles.size < 5
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Files (${selectedFiles.size}/5)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No files selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val context = LocalContext.current
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(selectedFiles) { uri ->
                    val fileName = getFileName(context, uri)
                    val role = determineRole(fileName, context, uri)
                    val icon = when (role) {
                        "WhatsApp Export" -> Icons.AutoMirrored.Filled.Chat
                        "ZIP" -> Icons.Default.FolderZip
                        "Member List" -> Icons.Default.Group
                        "Media Archive" -> Icons.Default.PermMedia
                        "Configuration" -> Icons.Default.Settings
                        "Text File" -> Icons.AutoMirrored.Filled.Article
                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                    }
                    
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fileName, fontWeight = FontWeight.Bold)
                                Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        
        // Media Import advice
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(12.dp))
                Text("For best performance, if your exported chat contains images, videos, documents or other media, compress all media into a single ZIP archive before importing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1 && cut < result!!.length - 1) {
            result = result!!.substring(cut + 1)
        }
    }
    return result ?: "Unknown File"
}

fun determineRole(fileName: String, context: android.content.Context? = null, uri: Uri? = null): String {
    val lower = fileName.lowercase()
    
    var role = "Unknown File"
    
    if (context != null && uri != null) {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        if (mimeType == "application/zip" || mimeType == "application/x-zip-compressed") role = "ZIP"
        else if (mimeType.startsWith("text/") && lower.contains("whatsapp")) role = "WhatsApp Export"
        else if (mimeType == "text/csv" || mimeType == "text/vcard" || mimeType == "text/x-vcard") role = "Member List"
        else if (mimeType == "application/json") role = "Configuration"
        else if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) role = "Media Archive"
    }

    if (role == "Unknown File" || role == "Text File") {
        role = when {
            lower.contains("whatsapp") && lower.endsWith(".txt") -> "WhatsApp Export"
            lower.endsWith(".zip") -> "ZIP"
            lower.endsWith(".csv") || lower.endsWith(".vcf") || lower.endsWith(".xlsx") -> "Member List"
            lower.endsWith(".json") || lower.endsWith(".xml") -> "Configuration"
            lower.endsWith(".jpg") || lower.endsWith(".mp4") || lower.endsWith(".png") || lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".pdf") || lower.endsWith(".gif") || lower.endsWith(".apk") -> "Media Archive"
            lower.endsWith(".txt") -> "Text File"
            else -> "Unknown File"
        }
    }
    
    if (context != null && uri != null && (role == "Unknown File" || role == "Text File" || role == "Member List")) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8))
            val firstLines = StringBuilder()
            for (i in 0..5) {
                val line = reader.readLine()
                if (line != null) firstLines.append(line).append("\n")
            }
            reader.close()
            val text = firstLines.toString().lowercase()
            
            if (Regex("\\d{1,2}/\\d{1,2}/\\d{2,4},? \\d{1,2}:\\d{2}").containsMatchIn(text) || Regex("\\[\\d{1,2}/\\d{1,2}/\\d{2,4},? \\d{1,2}:\\d{2}:\\d{2}\\]").containsMatchIn(text)) {
                role = "WhatsApp Export"
            } 
            else if (text.contains("begin:vcard") || text.contains("name,phone") || text.contains("participant") || text.contains("contact") || text.contains("member") || Regex("\\+?\\d{7,15}").containsMatchIn(text)) {
                role = "Member List"
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    return role
}

@Composable
fun Step2Participants(participantText: String, onTextChanged: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Participant List (Optional)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Import or paste the complete group member list to identify members who never appeared in the exported chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = participantText,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Paste Members Here") },
            placeholder = { Text("Krish\nRahul\nAman\nor\nKrish,+9198XXXX\nRahul,+9199XXXX") },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun Step3Summary(selectedFiles: List<Uri>, participantText: String, importState: com.example.ui.ImportState, isValidating: Boolean, setValidating: (Boolean) -> Unit) {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ChatViewModel>()
    
    var membersImportedStr by remember { mutableStateOf("Scanning...") }
    var participantsFoundStr by remember { mutableStateOf("Scanning...") }
    var mergedParticipantsStr by remember { mutableStateOf("Scanning...") }
    var duplicateNumbersStr by remember { mutableStateOf("Scanning...") }
    var duplicateRowsStr by remember { mutableStateOf("Scanning...") }
    var headerRowsStr by remember { mutableStateOf("Scanning...") }
    var silentMembersStr by remember { mutableStateOf("Scanning...") }
    var unmatchedMembersStr by remember { mutableStateOf("Scanning...") }
    var warningsStr by remember { mutableStateOf("0") }

    LaunchedEffect(selectedFiles, participantText) {
        try {
            setValidating(true)
            membersImportedStr = "Scanning..."
            participantsFoundStr = "Scanning..."
            mergedParticipantsStr = "Scanning..."
            duplicateNumbersStr = "Scanning..."
            duplicateRowsStr = "Scanning..."
            headerRowsStr = "Scanning..."
            silentMembersStr = "Scanning..."
            unmatchedMembersStr = "Scanning..."
            warningsStr = "0"
    
            var warnings = 0
            val allImportedMembers = mutableListOf<com.example.parser.ImportedMember>()
            var primaryChatUri: Uri? = null
            var chatText = ""
            
            withContext(Dispatchers.IO) {
                for (uri in selectedFiles) {
                    try {
                        val role = determineRole(getFileName(context, uri), context, uri)
                        if (role == "WhatsApp Export" || role == "Text File") {
                            primaryChatUri = uri
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8))
                                chatText = reader.readText()
                                reader.close()
                            }
                        } else if (role == "ZIP") {
                            warnings++ // Simple parser doesn't extract zip for preview, just logs warning
                        } else if (role == "Member List") {
                            val members = com.example.parser.MemberImportEngine.parseFile(context, uri)
                            allImportedMembers.addAll(members)
                        }
                    } catch (e: Exception) {
                        warnings++
                    }
                }
    
                if (participantText.isNotBlank()) {
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
    
                // Quick parse of chat text for participants
                var parsedChatParticipants = emptyList<com.example.parser.WhatsAppParser.ParsedParticipant>()
                if (chatText.isNotEmpty()) {
                    val lines = chatText.lines()
                    val result = com.example.parser.WhatsAppParser.parseChatExport(lines, 0L)
                    parsedChatParticipants = result.participants
                }
                
                // Use Matching Engine
                val (finalParticipants, stats) = com.example.engine.ParticipantMatchingEngine.matchParticipants(
                    0L,
                    parsedChatParticipants,
                    allImportedMembers,
                    null // Mock AI Service for UI preview
                )
                
                withContext(Dispatchers.Main) {
                    membersImportedStr = "${stats.membersImported}"
                    participantsFoundStr = "${stats.participantsFound}"
                    mergedParticipantsStr = "${stats.mergedParticipants}"
                    duplicateNumbersStr = "${stats.duplicateNumbersRemoved}"
                    duplicateRowsStr = "${stats.duplicateRowsRemoved}"
                    headerRowsStr = "${stats.headerRowsIgnored}"
                    silentMembersStr = "${stats.silentMembers}"
                    unmatchedMembersStr = "${stats.unmatchedMembers}"
                    warningsStr = "$warnings"
                }
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                setValidating(false)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Import Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SummaryRow(Icons.Default.Person, "Members Imported", membersImportedStr) }
                item { SummaryRow(Icons.AutoMirrored.Filled.Chat, "Participants Found", participantsFoundStr) }
                item { SummaryRow(Icons.Default.Merge, "Merged Participants", mergedParticipantsStr) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item { SummaryRow(Icons.Default.Delete, "Duplicate Numbers Removed", duplicateNumbersStr) }
                item { SummaryRow(Icons.Default.DeleteOutline, "Duplicate Rows Removed", duplicateRowsStr) }
                item { SummaryRow(Icons.Default.Cancel, "Header Rows Ignored", headerRowsStr) }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                item { SummaryRow(Icons.Default.VolumeOff, "Silent Members", silentMembersStr) }
                item { SummaryRow(Icons.Default.HelpOutline, "Unmatched Members", unmatchedMembersStr) }
                item { SummaryRow(Icons.Default.Warning, "Warnings", warningsStr) }
            }
        }
        
        if (importState is com.example.ui.ImportState.Loading) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Importing...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(importState.progressText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { importState.percentage },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
            }
        } else if (importState is com.example.ui.ImportState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Import Failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(importState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
fun SummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Medium)
        }
        Text(value, fontWeight = FontWeight.Bold)
    }
}
