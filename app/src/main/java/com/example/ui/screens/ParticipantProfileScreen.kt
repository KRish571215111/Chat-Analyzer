package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.Participant
import com.example.ui.AiState
import com.example.ui.ChatViewModel
import com.example.ui.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantProfileScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    initialParticipantId: Long,
    modifier: Modifier = Modifier
) {
    val participants by viewModel.currentParticipants.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val aiState by viewModel.aiState.collectAsState()

    var selectedParticipantId by remember(initialParticipantId) { mutableStateOf(initialParticipantId) }

    val selectedPart = remember(participants, selectedParticipantId) {
        participants.find { it.id == selectedParticipantId } ?: participants.firstOrNull()
    }

    // Calculations for the selected participant
    val partStats = remember(messages, selectedPart) {
        if (messages.isEmpty() || selectedPart == null) return@remember PartCalculatedStats()

        val pMessages = messages.filter { it.senderName == selectedPart.name }
        val totalMsgs = pMessages.size

        val questionsCount = pMessages.count { it.messageText.trim().endsWith("?") }
        val longestMsg = pMessages.maxByOrNull { it.messageText.length }?.messageText ?: "None"
        val shortestMsg = pMessages.filter { it.messageText.isNotBlank() }.minByOrNull { it.messageText.length }?.messageText ?: "None"

        // Most active hour
        val hourCounts = pMessages.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
        val topHour = hourCounts.maxByOrNull { it.value }?.key ?: 12

        // Conversation percentage
        val totalChatMessages = messages.count { !it.isSystem }.coerceAtLeast(1)
        val percentage = (totalMsgs.toFloat() / totalChatMessages) * 100f

        // Average message length
        val avgCharLen = if (totalMsgs > 0) selectedPart.characterCount / totalMsgs else 0

        // Estimate response speed
        var responseCount = 0
        var totalResponseMs = 0L
        for (i in 1 until messages.size) {
            val prev = messages[i - 1]
            val curr = messages[i]
            if (curr.senderName == selectedPart.name && prev.senderName != selectedPart.name && !prev.isSystem) {
                val diff = curr.timestamp - prev.timestamp
                if (diff in 0..(1000 * 60 * 60 * 2)) { // responded within 2 hours
                    responseCount++
                    totalResponseMs += diff
                }
            }
        }
        val avgResponseTimeText = if (responseCount > 0) {
            val avgMin = (totalResponseMs / responseCount) / (1000 * 60)
            if (avgMin == 0L) "Under 1 minute" else "$avgMin mins"
        } else {
            "10-15 mins (Estimated)"
        }

        PartCalculatedStats(
            questionsAsked = questionsCount,
            longestMessageText = if (longestMsg.length > 80) longestMsg.take(77) + "..." else longestMsg,
            shortestMessageText = shortestMsg,
            mostActiveHourText = String.format("%02d:00", topHour),
            percentage = percentage,
            avgCharLen = avgCharLen,
            avgResponseTimeText = avgResponseTimeText
        )
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Statistics", "Timeline", "Media", "Links")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Participant Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Compare */ }) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare")
                    }
                    IconButton(onClick = { /* Export */ }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Participant Selector
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants) { part ->
                    val isSelected = part.id == selectedParticipantId
                    val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val chipText = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(chipBg)
                            .clickable { selectedParticipantId = part.id }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("participant_chip_${part.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = part.name.take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = part.name,
                            color = chipText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            selectedPart?.let { part ->
                when (selectedTab) {
                    0 -> OverviewTab(
                        part = part,
                        partStats = partStats,
                        aiState = aiState,
                        viewModel = viewModel,
                        sessionId = sessionId
                    )
                    1 -> StatisticsTab(part = part, partStats = partStats)
                    2 -> TimelineTab(part = part, viewModel = viewModel, sessionId = sessionId)
                    3 -> MediaTab(part = part)
                    4 -> LinksTab(part = part)
                }
            }
        }
    }
}

@Composable
fun OverviewTab(
    part: Participant,
    partStats: PartCalculatedStats,
    aiState: AiState,
    viewModel: ChatViewModel,
    sessionId: Long
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Main Header Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = part.name.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = part.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Phone: ${part.phone ?: "Not Present"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Status: ${if (part.messageCount > 0) "Active" else "Silent Member"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = if (part.isFavorite) Color.Red else Color.Gray,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable {
                                                    viewModel.toggleFavoriteParticipant(part.id, !part.isFavorite)
                                                }
                                                .testTag("toggle_favorite_part_button")
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (part.isFavorite) "Pinned Contributor" else "Pin Contributor",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%.0f%%", partStats.percentage),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "Share",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Stats Numbers
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(
                                    title = "Message Count",
                                    value = part.messageCount.toString(),
                                    icon = Icons.Default.ChatBubble,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Media Count",
                                    value = part.mediaCount.toString(),
                                    icon = Icons.Default.Image,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard(
                                    title = "Word Count",
                                    value = part.wordCount.toString(),
                                    icon = Icons.Default.Create,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Character Count",
                                    value = part.characterCount.toString(),
                                    icon = Icons.Default.TextFields,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Key Behavioral Indicators
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Behavioral Indicators",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                IndicatorRow(
                                    label = "Questions Asked (?):",
                                    value = partStats.questionsAsked.toString()
                                )
                                IndicatorRow(
                                    label = "Shared Links:",
                                    value = part.linkCount.toString()
                                )
                                IndicatorRow(
                                    label = "Shared Media files:",
                                    value = part.mediaCount.toString()
                                )
                                IndicatorRow(
                                    label = "Favorite Emoji:",
                                    value = part.favoriteEmoji ?: "None"
                                )
                                IndicatorRow(
                                    label = "Avg Message Character Length:",
                                    value = "${partStats.avgCharLen} characters"
                                )
                                IndicatorRow(
                                    label = "Est. Response Speed:",
                                    value = partStats.avgResponseTimeText
                                )
                                IndicatorRow(
                                    label = "Most Active Hour:",
                                    value = partStats.mostActiveHourText
                                )
                            }
                        }
                    }

                    // Message Length Highlights
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Longest Message",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${partStats.longestMessageText}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    // AI Personality Profile Analysis
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "AI Personality Summary",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                when (val state = aiState) {
                                    is AiState.Loading -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Synthesizing personality traits...")
                                        }
                                    }
                                    is AiState.Success -> {
                                        Text(
                                            state.response,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        Button(onClick = { viewModel.clearAiState() }) {
                                            Text("Done")
                                        }
                                    }
                                    is AiState.Error -> {
                                        Text(
                                            "Error: ${state.message}",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    else -> {
                                        Text(
                                            "Run generative AI to analyze Alice's or Krish's writing pattern, sentiment score, conversation role (catalyst vs responder), and preferred topics.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                viewModel.requestAiConsent(
                                                    "Analyze the participant ${part.name} who contributed ${part.messageCount} messages. Detail their personality, communication style, tone, favorite topics, and estimated sentiment score."
                                                )
                                            },
                                            modifier = Modifier.testTag("ai_personality_button")
                                        ) {
                                            Text("Generate AI Profile")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Timeline trigger
                    item {
                        Button(
                            onClick = {
                                viewModel.setFilterSender(part.name)
                                viewModel.navigateTo(Screen.MessageViewer(sessionId, part.name))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("view_part_timeline_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspect Participant Messages")
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
}

@Composable
fun StatisticsTab(part: Participant, partStats: PartCalculatedStats) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Text("General Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    IndicatorRow("Total Messages", part.messageCount.toString())
                    IndicatorRow("Total Words", part.wordCount.toString())
                    IndicatorRow("Total Characters", part.characterCount.toString())
                    val avgWords = if (part.messageCount > 0) part.wordCount / part.messageCount else 0
                    IndicatorRow("Avg Words/Message", avgWords.toString())
                    IndicatorRow("Avg Characters/Message", partStats.avgCharLen.toString())
                    IndicatorRow("Questions Asked", partStats.questionsAsked.toString())
                    IndicatorRow("Replies Given", part.repliesGiven.toString())
                    IndicatorRow("Total Emoji Used", part.emojiCount.toString())
                    IndicatorRow("Links Shared", part.linkCount.toString())
                }
            }
        }

        item {
            Text("Averages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    IndicatorRow("Avg Msgs / Day", String.format("%.1f", part.averageDailyActivity))
                    IndicatorRow("Avg Msgs / Week", String.format("%.1f", part.averageDailyActivity * 7))
                    IndicatorRow("Avg Msgs / Month", String.format("%.1f", part.averageDailyActivity * 30))
                }
            }
        }

        item {
            Text("Media & Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    IndicatorRow("Total Media", part.mediaCount.toString())
                    IndicatorRow("Images", part.imagesShared.toString())
                    IndicatorRow("Videos", part.videosShared.toString())
                    IndicatorRow("GIFs", part.gifsShared.toString())
                    IndicatorRow("Documents", part.documentsShared.toString())
                    IndicatorRow("Voice Notes", part.voiceNotesShared.toString())
                    IndicatorRow("Audio", part.audioShared.toString())
                    IndicatorRow("Contacts", part.contactsShared.toString())
                    IndicatorRow("Locations", part.locationsShared.toString())
                    IndicatorRow("Polls", "0")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun TimelineTab(part: Participant, viewModel: ChatViewModel, sessionId: Long) {
    var selectedTimelineView by remember { mutableStateOf("Daily") }
    val views = listOf("Daily", "Weekly", "Monthly", "Yearly")
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            ScrollableTabRow(
                selectedTabIndex = views.indexOf(selectedTimelineView),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                views.forEach { viewName ->
                    Tab(
                        selected = selectedTimelineView == viewName,
                        onClick = { selectedTimelineView = viewName },
                        text = { Text(viewName) }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Interactive $selectedTimelineView Chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Chart visualizing message trends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Text("Heatmaps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Hourly Activity Heatmap\n(Most active: ${String.format("%02d:00", part.mostActiveHour.coerceAtLeast(0))})", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Weekday Activity Heatmap", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun MediaTab(part: Participant) {
    var selectedMediaType by remember { mutableStateOf("Images") }
    val mediaTypes = listOf("Images", "Videos", "GIFs", "Documents", "Voice Notes", "Audio", "Locations")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = mediaTypes.indexOf(selectedMediaType),
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            mediaTypes.forEach { type ->
                Tab(
                    selected = selectedMediaType == type,
                    onClick = { selectedMediaType = type },
                    text = { Text(type) }
                )
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No $selectedMediaType shared by ${part.name}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LinksTab(part: Participant) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                if (part.linkCount == 0) {
                    Text("No links shared by ${part.name}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${part.linkCount} links shared by ${part.name}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Link explorer coming soon.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun IndicatorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

data class PartCalculatedStats(
    val questionsAsked: Int = 0,
    val longestMessageText: String = "None",
    val shortestMessageText: String = "None",
    val mostActiveHourText: String = "12:00",
    val percentage: Float = 0f,
    val avgCharLen: Int = 0,
    val avgResponseTimeText: String = "15 mins"
)
