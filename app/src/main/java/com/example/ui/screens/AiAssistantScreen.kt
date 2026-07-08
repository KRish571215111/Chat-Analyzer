package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiState
import com.example.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Ask AI", "Summaries", "Analysis", "Recommendations", "History & Reports")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Z.AI Intelligence") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> AskAiTab(viewModel)
                    1 -> SummariesTab(viewModel) { selectedTab = 0 }
                    2 -> AnalysisTab(viewModel) { selectedTab = 0 }
                    3 -> RecommendationsTab(viewModel) { selectedTab = 0 }
                    4 -> HistoryReportsTab(viewModel) { selectedTab = 0 }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiTab(viewModel: ChatViewModel) {
    val aiState by viewModel.aiState.collectAsState()
    var promptInput by remember { mutableStateOf("") }
    
    val presetPrompts = listOf(
        "Who is the most active member?",
        "What is the overall sentiment of this chat?",
        "Summarize the main topics discussed.",
        "List the most shared links.",
        "Identify any conflicts or arguments.",
        "What are the typical active hours?"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                when (val state = aiState) {
                    is AiState.Idle -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Ask the group intelligence...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is AiState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 48.dp, horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Synthesizing Intelligence", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Reading context, cross-referencing timeline events, and analyzing semantic subtext...", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is AiState.Success -> {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Synthesized Intelligence", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = state.response, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                        }
                    }
                    is AiState.Error -> {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Query Failed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick Prompt Cards
        if (aiState is AiState.Idle) {
            LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presetPrompts.chunked(2)) { rowPrompts ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPrompts.forEach { preset ->
                            Card(
                                modifier = Modifier.weight(1f).clickable {
                                    promptInput = preset
                                    viewModel.requestAiConsent(preset)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(preset, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Input Field
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                modifier = Modifier.weight(1f).testTag("ai_prompt_input"),
                placeholder = { Text("Ask anything about this chat...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { if (promptInput.isNotBlank()) viewModel.requestAiConsent(promptInput) },
                enabled = promptInput.isNotBlank() && aiState !is AiState.Loading,
                modifier = Modifier.size(48.dp).background(color = if (promptInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (promptInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SummariesTab(viewModel: ChatViewModel, onAction: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Generate Summaries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        
        val summaryTypes = listOf(
            "Executive Summary" to "Short, detailed, or full report.",
            "Timeline Summary" to "Chronological timeline of events.",
            "Participant Summary" to "Activity pattern for individuals.",
            "Today's Summary" to "What happened today?"
        )
        
        items(summaryTypes) { (title, desc) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.requestAiConsent("Generate a $title"); onAction() }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisTab(viewModel: ChatViewModel, onAction: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Deep Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        
        val analysisTypes = listOf(
            "Sentiment Analysis" to "Identify positive, negative, and neutral tones.",
            "Topic Modeling" to "Extract key topics and themes.",
            "Relationship Graph" to "Analyze interactions between participants.",
            "Conflict Detection" to "Identify potential arguments or disagreements."
        )
        
        items(analysisTypes) { (title, desc) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.requestAiConsent("Perform $title analysis"); onAction() }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationsTab(viewModel: ChatViewModel, onAction: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("AI Recommendations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        
        val recommendationTypes = listOf(
            "Action Items" to "Extract tasks and action items from the conversation.",
            "Next Steps" to "Suggest potential next steps based on the discussion.",
            "Communication Tips" to "Provide feedback on communication style and effectiveness."
        )
        
        items(recommendationTypes) { (title, desc) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.requestAiConsent("Provide recommendations for $title"); onAction() }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryReportsTab(viewModel: ChatViewModel, onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No AI History Yet", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your past AI queries and generated reports will appear here.", textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }
}
