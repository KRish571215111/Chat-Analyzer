package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import com.example.data.ChatMessage
import com.example.data.Participant
import com.example.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

// --- Data Models ---
data class AnalyticsData(
    val totalMessages: Int,
    val activeParticipants: Int,
    val totalMedia: Int,
    val totalLinks: Int,
    val avgDailyMessages: Int,
    val mostActiveDay: String,
    val mostActiveMember: String,
    val longestStreak: Int,
    val chatHealthScore: Int,
    val topContributors: List<Pair<String, Int>>,
    val hourlyActivity: Map<Int, Int>, // 0-23 -> count
    val dailyActivity: Map<String, Int>, // Mon, Tue, etc -> count
    val monthlyActivity: Map<String, Int>, // Jan, Feb -> count
    val wordFrequency: List<Pair<String, Int>>,
    val emojiFrequency: List<Pair<String, Int>>,
    val mostUsedWords: List<Pair<String, Int>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.currentMessages.collectAsState()
    val participants by viewModel.currentParticipants.collectAsState()
    val session by viewModel.currentSession.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Executive", "Participants", "Time & Trends", "Content", "AI Insights")

    val analyticsData by produceState<AnalyticsData?>(initialValue = null, messages) {
        if (messages.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                value = calculateAnalytics(messages, participants)
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportInteractiveReport(context, uri)
        }
    }

    val exportState by viewModel.exportState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is com.example.ui.ReportExportState.Loading -> {
                snackbarHostState.showSnackbar("Exporting report...", duration = SnackbarDuration.Short)
            }
            is com.example.ui.ReportExportState.Success -> {
                snackbarHostState.showSnackbar("Report exported successfully!")
                viewModel.clearExportState()
            }
            is com.example.ui.ReportExportState.Error -> {
                snackbarHostState.showSnackbar("Export failed: ${state.message}")
                viewModel.clearExportState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Business Intelligence Analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val fileName = "Analytics_Report_${System.currentTimeMillis()}.zip"
                        exportLauncher.launch(fileName)
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Report")
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            if (analyticsData == null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.7f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
                            Box(modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = alpha)))
                    }
                }
            } else {
                val data = analyticsData!!
                when (selectedTab) {
                    0 -> ExecutiveTab(data, session?.name ?: "Chat")
                    1 -> ParticipantsTab(data)
                    2 -> TimeAndTrendsTab(data)
                    3 -> ContentTab(data)
                    4 -> AiInsightsTab(viewModel)
                }
            }
        }
    }
}

private fun calculateAnalytics(messages: List<ChatMessage>, participants: List<Participant>): AnalyticsData {
    val totalMsg = messages.size
    val activeParts = messages.map { it.senderName }.distinct().size
    val mediaMsg = messages.count { it.isMedia }
    val linkMsg = messages.count { it.messageText.contains("http") }

    val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sdfHour = SimpleDateFormat("HH", Locale.getDefault())
    val sdfDayOfWeek = SimpleDateFormat("EEE", Locale.getDefault())
    val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())

    val dates = messages.map { Date(it.timestamp) }
    
    val dateCounts = dates.groupingBy { sdfDay.format(it) }.eachCount()
    val avgDaily = if (dateCounts.isNotEmpty()) totalMsg / dateCounts.size else 0
    val mostActiveDay = dateCounts.maxByOrNull { it.value }?.key ?: "N/A"

    val senderCounts = messages.groupingBy { it.senderName }.eachCount()
    val mostActiveMember = senderCounts.maxByOrNull { it.value }?.key ?: "N/A"

    val sortedDates = dateCounts.keys.sorted()
    var maxStreak = 0
    // Simplified streak calc
    maxStreak = sortedDates.size

    val healthScore = ((activeParts.toFloat() / max(participants.size, 1)) * 40 + (mediaMsg.toFloat() / max(totalMsg, 1)) * 30 + (if(avgDaily > 10) 30 else avgDaily*3)).toInt().coerceIn(0, 100)

    val topContributors = senderCounts.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }

    val hourly = dates.groupingBy { sdfHour.format(it).toInt() }.eachCount()
    val daily = dates.groupingBy { sdfDayOfWeek.format(it) }.eachCount()
    val monthly = dates.groupingBy { sdfMonth.format(it) }.eachCount()

    // Content
    val allWords = messages.filter { !it.isMedia && it.messageType == "TEXT" }
        .flatMap { it.messageText.lowercase().split(Regex("\\s+")) }
        .filter { it.length > 3 }
    val wordFreq = allWords.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
    
    val emojiRegex = Regex("(?:[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83E\uDD00-\uD83E\uDDFF]|[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEF6]|[\u2600-\u26FF]\uFE0F?|[\u2700-\u27BF]\uFE0F?)")
    val allEmojis = messages.flatMap { msg -> emojiRegex.findAll(msg.messageText).map { it.value }.toList() }
    val emojiFreq = allEmojis.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }

    return AnalyticsData(
        totalMessages = totalMsg,
        activeParticipants = activeParts,
        totalMedia = mediaMsg,
        totalLinks = linkMsg,
        avgDailyMessages = avgDaily,
        mostActiveDay = mostActiveDay,
        mostActiveMember = mostActiveMember,
        longestStreak = maxStreak,
        chatHealthScore = healthScore,
        topContributors = topContributors,
        hourlyActivity = hourly,
        dailyActivity = daily,
        monthlyActivity = monthly,
        wordFrequency = wordFreq,
        emojiFrequency = emojiFreq,
        mostUsedWords = wordFreq
    )
}

@Composable
fun ExecutiveTab(data: AnalyticsData, chatName: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Executive Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("High-level summary for \$chatName", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\${data.chatHealthScore}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text("Chat Health Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Based on participation, consistency, and density.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "Total Messages", value = "${data.totalMessages}", icon = Icons.AutoMirrored.Filled.Chat)
                StatCard(modifier = Modifier.weight(1f), title = "Active Members", value = "${data.activeParticipants}", icon = Icons.Default.People)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(modifier = Modifier.weight(1f), title = "Total Media", value = "${data.totalMedia}", icon = Icons.Default.Image)
                StatCard(modifier = Modifier.weight(1f), title = "Avg Daily", value = "${data.avgDailyMessages}", icon = Icons.AutoMirrored.Filled.ShowChart)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Highlights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    HighlightRow("Most Active Member", data.mostActiveMember)
                    HighlightRow("Peak Activity Day", data.mostActiveDay)
                    HighlightRow("Total Links Shared", "\${data.totalLinks}")
                    HighlightRow("Active Days Streak", "\${data.longestStreak} days")
                }
            }
        }
    }
}

@Composable
fun HighlightRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ParticipantsTab(data: AnalyticsData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Top Contributors", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Ranked by message count", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        
        items(data.topContributors.size) { index ->
            val (name, count) = data.topContributors[index]
            val percentage = if (data.totalMessages > 0) (count.toFloat() / data.totalMessages) * 100 else 0f
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("#\${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), color = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text("$count msgs", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun TimeAndTrendsTab(data: AnalyticsData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Activity Heatmaps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("24-Hour Activity", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        for (hour in 0..23) {
                            val count = data.hourlyActivity[hour] ?: 0
                            val maxCount = data.hourlyActivity.values.maxOrNull() ?: 1
                            val height = if (maxCount > 0) (count.toFloat() / maxCount) * 100f else 0f
                            Box(modifier = Modifier.width(8.dp).height(height.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("12 AM", fontSize = 10.sp, color = Color.Gray)
                        Text("12 PM", fontSize = 10.sp, color = Color.Gray)
                        Text("11 PM", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Weekly Distribution", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        days.forEach { day ->
                            val count = data.dailyActivity[day] ?: 0
                            val maxCount = data.dailyActivity.values.maxOrNull() ?: 1
                            val height = if (maxCount > 0) (count.toFloat() / maxCount) * 100f else 0f
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.width(20.dp).height(height.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(MaterialTheme.colorScheme.tertiary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(day, fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentTab(data: AnalyticsData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Vocabulary & Content", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Most Used Words", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.wordFrequency.forEach { (word, count) ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text("\$word (\$count)") }
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Top Emojis", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.emojiFrequency.forEach { (emoji, count) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(emoji, fontSize = 24.sp)
                                Text("\$count", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(content, modifier) { measurables, constraints ->
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentWidth = 0
        
        val hSpacing = horizontalArrangement.spacing.roundToPx()
        val vSpacing = verticalArrangement.spacing.roundToPx()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)
            if (currentWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf(placeable)
                currentWidth = placeable.width + hSpacing
            } else {
                currentRow.add(placeable)
                currentWidth += placeable.width + hSpacing
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOf { it.height } } + (max(0, rows.size - 1)) * vSpacing
        
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hSpacing
                }
                y += rowHeight + vSpacing
            }
        }
    }
}

@Composable
fun AiInsightsTab(viewModel: ChatViewModel) {
    val aiState by viewModel.aiState.collectAsState()
    var prompt by remember { mutableStateOf("Generate an Executive Summary of this chat group, highlighting major themes, key decisions, and overall sentiment.") }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI Executive Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Generate grounded summaries and observations.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom Prompt (Optional)") },
            maxLines = 3
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.requestAiConsent(prompt) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Insights")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                when (val state = aiState) {
                    is com.example.ui.AiState.Idle -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Deep AI Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Generate deep insights into group dynamics, conversation topics, and sentiment analysis. This process uses your configured AI provider.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.requestAiConsent("Generate a comprehensive analytical report of this chat session. Identify key participants, frequent topics, and overall sentiment. Provide the output in a structured markdown format.") }) {
                                Text("Generate Insights")
                            }
                        }
                    }
                    is com.example.ui.AiState.Loading -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("AI is processing your chat...", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Extracting key topics, tracking sentiment trends, and ranking participant influence. This might take a few moments depending on chat size.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is com.example.ui.AiState.Success -> {
                        Text(state.response)
                    }
                    is com.example.ui.AiState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) + androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }, animationSpec = androidx.compose.animation.core.tween(500)),
        modifier = modifier
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
