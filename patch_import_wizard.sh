#!/bin/bash

# Target file
FILE="app/src/main/java/com/example/ui/screens/ImportWizardScreen.kt"

# Replace the Step3Summary composable entirely
sed -i '/@Composable/,$!b;/fun Step3Summary/,/^}$/!b;//!d;/fun Step3Summary/!d' $FILE

# The script above will delete Step3Summary. Let's append the correct code.
cat << 'INNER_EOF' >> $FILE
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
                var parsedChatParticipants = emptyList<com.example.data.Participant>()
                if (chatText.isNotEmpty()) {
                    val lines = chatText.lines()
                    val result = com.example.parser.WhatsAppParser.parseChatExport(lines, 0L)
                    parsedChatParticipants = result.participants
                }
                
                // Use Matching Engine
                val (finalParticipants, stats) = com.example.engine.ParticipantMatchingEngine.matchParticipants(
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
INNER_EOF

