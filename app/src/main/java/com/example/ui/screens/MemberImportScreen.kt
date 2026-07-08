package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Participant
import com.example.parser.ImportedMember
import com.example.parser.MemberImportEngine
import com.example.ui.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberImportScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var importedMembers by remember { mutableStateOf<List<ImportedMember>?>(null) }
    var participants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var confirmClassification by remember { mutableStateOf<Pair<Uri, com.example.parser.ClassificationResult>?>(null) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isImporting = true
                errorMessage = null
                try {
                    val classification = MemberImportEngine.classifyContent(context, uri, viewModel.aiService)
                    if (!classification.isConfident) {
                        confirmClassification = Pair(uri, classification)
                        isImporting = false
                        return@launch
                    }
                    val parsed = MemberImportEngine.parseFile(context, uri)
                    importedMembers = parsed
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = e.localizedMessage ?: "Failed to parse the contact list. Please verify the CSV header names."
                } finally {
                    isImporting = false
                }
            }
        }
    }
    
    LaunchedEffect(sessionId) {
        viewModel.getParticipantsForSession(sessionId).collect {
            participants = it
        }
    }
    
    if (confirmClassification != null) {
        val (uri, classification) = confirmClassification!!
        AlertDialog(
            onDismissRequest = { confirmClassification = null },
            title = { Text("Confirm Import") },
            text = { Text("We think this is a ${classification.detectedType} (${(classification.confidence * 100).toInt()}% confidence). Continue?") },
            confirmButton = {
                Button(onClick = {
                    confirmClassification = null
                    coroutineScope.launch {
                        isImporting = true
                        try {
                            val parsed = MemberImportEngine.parseFile(context, uri)
                            importedMembers = parsed
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = e.localizedMessage ?: "Failed to parse the contact list."
                        } finally {
                            isImporting = false
                        }
                    }
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClassification = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Import Failed") },
            text = { Text(errorMessage ?: "An unknown error occurred during import.") },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Members") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (importedMembers != null) {
                        TextButton(onClick = { 
                            coroutineScope.launch {
                                viewModel.mergeImportedMembers(sessionId, importedMembers!!)
                                onBack()
                            }
                        }) {
                            Text("Confirm", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (importedMembers == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isImporting) {
                        CircularProgressIndicator()
                    } else {
                        EmptyStateView(
                            icon = Icons.Default.FileUpload,
                            title = "Import Contacts",
                            description = "Select a CSV, VCF, JSON, TXT, or XLSX file to import members.",
                            primaryActionText = "Choose File",
                            onPrimaryAction = {
                                filePickerLauncher.launch("*/*")
                            },
                            helpfulTip = "The engine automatically detects the format and removes duplicates."
                        )
                    }
                }
            } else {
                ImportPreview(
                    importedMembers = importedMembers!!,
                    participants = participants,
                    onClear = { importedMembers = null }
                )
            }
        }
    }
}

@Composable
fun ImportPreview(
    importedMembers: List<ImportedMember>,
    participants: List<Participant>,
    onClear: () -> Unit
) {
    // Analytics
    val totalImported = importedMembers.size
    
    // Deduplication logic
    val uniqueImported = mutableListOf<ImportedMember>()
    val seenPhones = mutableSetOf<String>()
    val seenNames = mutableSetOf<String>()
    var duplicateNumbers = 0
    var duplicateMembers = 0
    
    for (m in importedMembers) {
        if (m.normalizedPhone != null) {
            if (!seenPhones.add(m.normalizedPhone)) {
                duplicateNumbers++
                continue
            }
        }
        if (!seenNames.add(m.originalName)) {
            duplicateMembers++
            // we still add them if phone is unique, but it's a name collision
        }
        uniqueImported.add(m)
    }
    
    // Match with existing participants
    val participantMap = participants.associateBy { it.normalizedPhone ?: "" }.filterKeys { it.isNotEmpty() }
    val participantNameMap = participants.associateBy { it.name }
    
    var detectedParticipants = 0
    var silentMembers = 0
    val matchedParticipantIds = mutableSetOf<Long>()
    
    uniqueImported.forEach { m ->
        val matchedByPhone = m.normalizedPhone?.let { participantMap[it] }
        val matchedByName = m.originalName.let { participantNameMap[it] }
        
        val matched = matchedByPhone ?: matchedByName
        if (matched != null) {
            detectedParticipants++
            matchedParticipantIds.add(matched.id)
        } else {
            silentMembers++
        }
    }
    
    val totalMembers = uniqueImported.size
    val unknownParticipants = participants.count { !matchedParticipantIds.contains(it.id) }
    val missingMembers = unknownParticipants
    val participationPercentage = if (totalMembers > 0) (detectedParticipants * 100) / totalMembers else 0
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Import Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Members: $totalMembers")
                        Text("Detected Participants: $detectedParticipants")
                        Text("Silent Members: $silentMembers")
                        Text("Missing Members: $missingMembers")
                    }
                    Column {
                        Text("Unknown Participants: $unknownParticipants")
                        Text("Duplicate Members: $duplicateMembers")
                        Text("Duplicate Numbers: $duplicateNumbers")
                        Text("Participation: $participationPercentage%")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Preview (${uniqueImported.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClear) { Text("Clear") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uniqueImported) { member ->
                val matched = (member.normalizedPhone?.let { participantMap[it] } != null) || (member.originalName.let { participantNameMap[it] } != null)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (matched) Icons.Default.Check else Icons.Default.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(member.originalName.ifBlank { "Unknown Name" }, fontWeight = FontWeight.SemiBold)
                            Text(member.originalPhone ?: "No Phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!matched) {
                                Text("No messages found in this exported chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
