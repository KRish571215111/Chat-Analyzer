package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.Participant
import com.example.ui.AiState
import com.example.ui.ChatViewModel
import com.example.ui.ReportExportState
import com.example.ui.Screen
import com.example.ui.components.ActivityHeatmap
import com.example.ui.components.ActivityLineChart
import com.example.ui.components.SimpleBarChart
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDashboardScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session by viewModel.currentSession.collectAsState()
    val participants by viewModel.currentParticipants.collectAsState()
    val rawMessages by viewModel.currentMessages.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var activeTimeFilter by remember { mutableStateOf("All Time") }
    var showExportDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportInteractiveReport(context, uri)
            showExportDialog = true
        }
    }

    // Explicit type inference to satisfy compiler
    val messages: List<ChatMessage> = remember(rawMessages, activeTimeFilter) {
        if (rawMessages.isEmpty() || activeTimeFilter == "All Time") return@remember rawMessages

        val maxTimestamp = rawMessages.maxOfOrNull { m: ChatMessage -> m.timestamp } ?: System.currentTimeMillis()
        val daysLimit = when (activeTimeFilter) {
            "7D" -> 7L
            "30D" -> 30L
            "3M" -> 90L
            "6M" -> 180L
            else -> 36500L
        }
        val msLimit = daysLimit * 24 * 60 * 60 * 1000L
        rawMessages.filter { m: ChatMessage -> m.timestamp >= maxTimestamp - msLimit }
    }

    // Calculations based on actual filtered data
    val stats: DashboardStats = remember(messages, participants) {
        if (messages.isEmpty()) return@remember DashboardStats()

        val totalMsg = messages.size
        val systemCount = messages.count { it.isSystem }
        val userMessages = messages.filter { !it.isSystem }

        val firstMsgTimestamp = messages.minOfOrNull { m: ChatMessage -> m.timestamp } ?: 0L
        val lastMsgTimestamp = messages.maxOfOrNull { m: ChatMessage -> m.timestamp } ?: 0L
        val diffMs = lastMsgTimestamp - firstMsgTimestamp
        val daysActive = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(1L) + 1L

        val mediaCount = messages.count { it.isMedia }
        val totalWords = messages.sumOf { m: ChatMessage -> m.messageText.split("\\s+".toRegex()).count { it.isNotBlank() } }
        val totalEmojis = messages.sumOf { m: ChatMessage -> m.messageText.count { Character.isSurrogate(it) } } / 2
        val totalLinks = messages.sumOf { m: ChatMessage -> if (m.messageText.contains("http://") || m.messageText.contains("https://")) 1 else 0 }

        val avgMsgPerDay = (totalMsg.toFloat() / daysActive).coerceAtLeast(0.1f)
        val activityScore = ((totalMsg * 0.5f + totalWords * 0.1f + mediaCount * 1.5f) / daysActive).toInt().coerceAtLeast(1)

        val weekdayMap = mutableMapOf<Int, Int>()
        val hourMap = mutableMapOf<Int, Int>()

        userMessages.forEach { msg ->
            val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val heatmapDay = when (dayOfWeek) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
            weekdayMap[heatmapDay] = (weekdayMap[heatmapDay] ?: 0) + 1

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hourMap[hour] = (hourMap[hour] ?: 0) + 1
        }

        DashboardStats(
            daysActive = daysActive,
            mediaCount = mediaCount,
            totalWords = totalWords,
            totalEmojis = totalEmojis,
            totalLinks = totalLinks,
            avgMsgPerDay = avgMsgPerDay,
            activityScore = activityScore,
            weekdayMap = weekdayMap,
            hourMap = hourMap
        )
    }

    val activeHoursData = remember(stats.hourMap) {
        listOf("00", "04", "08", "12", "16", "20").map { label ->
            val hrVal = label.toInt()
            val totalForInterval = (0..3).sumOf { stats.hourMap[hrVal + it] ?: 0 }
            Pair(label, totalForInterval.toFloat())
        }
    }

    // Prepare line chart data
    val lineChartData = remember(stats.weekdayMap) {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        days.mapIndexed { index, name ->
            Pair(name, (stats.weekdayMap[index + 1] ?: 0).toFloat())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            session?.name ?: "Chat Dashboard",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.MessageViewer(session?.id ?: 0)) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.Participants(session?.id ?: 0)) }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import New Chat") },
                            onClick = { 
                                showMenu = false
                                viewModel.navigateTo(Screen.Home)
                            },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Quick Report") },
                            onClick = { 
                                showMenu = false
                                val safeName = session?.name?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "Chat"
                                createDocumentLauncher.launch("${safeName}_Interactive_Report.zip")
                            },
                            leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Banner
            item {
                session?.let {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val rangeText = "${sdf.format(Date(it.dateStart))} - ${sdf.format(Date(it.dateEnd))}"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        border = RowDefaults.cardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Date Range",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "WhatsApp",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                rangeText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                InfoColumn(
                                    label = "Active Days",
                                    value = stats.daysActive.toString(),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                InfoColumn(
                                    label = "Msg / Day",
                                    value = String.format("%.1f", stats.avgMsgPerDay),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                InfoColumn(
                                    label = "Activity Score",
                                    value = stats.activityScore.toString(),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Quick Navigation Hub
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        text = "Messages",
                        icon = Icons.Default.ChatBubbleOutline,
                        onClick = { viewModel.navigateTo(Screen.MessageViewer(session?.id ?: 0L)) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        onClick = { viewModel.navigateTo(Screen.MediaGallery(session?.id ?: 0L)) },
                        modifier = Modifier.weight(1f).testTag("media_gallery_button")
                    )
                    ActionButton(
                        text = "Members",
                        icon = Icons.Default.PeopleOutline,
                        onClick = {
                            if (participants.isNotEmpty()) {
                                viewModel.navigateTo(
                                    Screen.ParticipantProfile(
                                        session?.id ?: 0L,
                                        participants.first().id
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Key Statistics 2x2 Grid (Replica of Mockup overview cards)
            item {
                Column {
                    Text(
                        "Key Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                title = "Total Members",
                                value = participants.size.toString(),
                                icon = Icons.Outlined.Groups,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Participants Found",
                                value = participants.count { it.messageCount > 0 }.toString(),
                                icon = Icons.Outlined.Chat,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                title = "Silent Members",
                                value = participants.count { it.messageCount == 0 }.toString(),
                                icon = Icons.Outlined.PersonOff,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Total Messages",
                                value = messages.size.toString(),
                                icon = Icons.Outlined.Forum,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(
                                title = "Active Days",
                                value = stats.daysActive.toString(),
                                icon = Icons.Outlined.CalendarToday,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Media Shared",
                                value = stats.mediaCount.toString(),
                                icon = Icons.Outlined.Image,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Activity Overview Section
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Activity Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // Time filters chips row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filters = listOf("7D", "30D", "3M", "All Time")
                            filters.forEach { filter ->
                                val isActive = activeTimeFilter == filter
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { activeTimeFilter = filter }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        filter,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Curve line chart for premium vibes!
                    ActivityLineChart(data = lineChartData)
                }
            }

            // Activity Details & Busiest spots
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Find busiest weekday safely
                    val busiestDayIndex = if (stats.weekdayMap.isNotEmpty()) stats.weekdayMap.maxByOrNull { entry -> entry.value }?.key ?: 1 else 1
                    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    val busiestDayName = dayNames.getOrElse(busiestDayIndex - 1) { "Sunday" }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = RowDefaults.cardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Busiest Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(busiestDayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Find busiest hour range safely
                    val busiestHour = if (stats.hourMap.isNotEmpty()) stats.hourMap.maxByOrNull { entry -> entry.value }?.key ?: 20 else 20
                    val hourRangeText = String.format("%02d:00 - %02d:00", busiestHour, (busiestHour + 1) % 24)

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = RowDefaults.cardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Busiest Hour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(hourRangeText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // AI Insights Executive summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = RowDefaults.cardBorder()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "AI Executive Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(modifier = Modifier.animateContentSize()) {
                            when (val state = aiState) {
                                is AiState.Loading -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Generating professional insights...")
                                    }
                                }
                                is AiState.Success -> {
                                    Text(
                                        state.response,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = { viewModel.clearAiState() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Ask Something Else")
                                    }
                                }
                                is AiState.Error -> {
                                    Text(
                                        "Error: ${state.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    Button(onClick = { viewModel.requestAiConsent("Summarize this conversation and extract decision highlights.") }) {
                                        Text("Try Again")
                                    }
                                }
                                else -> {
                                    Text(
                                        "Let the Z.AI model process the chat subset to detect decisions, positive/negative arguments, announcements, and personality highlights.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { viewModel.requestAiConsent("Summarize this conversation and extract decision highlights.") },
                                            modifier = Modifier.weight(1f).testTag("ai_summarize_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("AI Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.requestAiConsent("Analyze the active participants and summarize who talked most and what they discussed.") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("AI Members", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Charts hourly
            item {
                Column {
                    Text(
                        "Activity Frequency Charts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ActivityHeatmap(activityDays = stats.weekdayMap)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Hourly Distribution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SimpleBarChart(data = activeHoursData)
                }
            }

            // Top Contributors list with visual percentage lines
            item {
                Text(
                    "Top Contributors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            val totalMsgCount = messages.size.coerceAtLeast(1)
            items(participants.take(5)) { part ->
                val percentage = (part.messageCount.toFloat() / totalMsgCount).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateTo(Screen.ParticipantProfile(session?.id ?: 0L, part.id))
                        }
                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = part.name.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                part.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                String.format("%.1f%%", percentage * 100f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Contribution visual line bar
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${part.messageCount} messages • ${part.wordCount} words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                TextButton(
                    onClick = { viewModel.navigateTo(Screen.Participants(session?.id ?: 0L)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View All Participants")
                }
            }

            item {
                LeastActivePreview(
                    participants = participants,
                    totalMsgCount = totalMsgCount,
                    onParticipantClick = { partId -> viewModel.navigateTo(Screen.ParticipantProfile(session?.id ?: 0L, partId)) }
                )
            }

            item {
                MediaOverview(
                    mediaCount = stats.mediaCount,
                    totalLinks = stats.totalLinks,
                    onMediaClick = { viewModel.navigateTo(Screen.MediaGallery(session?.id ?: 0L)) }
                )
            }

            item {
                QuickActionsGrid(
                    viewModel = viewModel,
                    sessionId = session?.id ?: 0L
                )
            }

            item {
                BookmarksFavoritesPreview(
                    onViewBookmarks = { viewModel.navigateTo(Screen.Bookmarks(session?.id ?: 0L)) }
                )
            }

            // Report Sharing Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { 
                            val safeName = session?.name?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "Chat"
                            createDocumentLauncher.launch("${safeName}_Interactive_Report.zip")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Interactive Report (HTML)", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (exportState !is ReportExportState.Loading && exportState !is ReportExportState.Progress) {
                    showExportDialog = false 
                    viewModel.clearExportState()
                }
            },
            title = {
                Text("Exporting Report")
            },
            text = {
                when (val state = exportState) {
                    is ReportExportState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Generating self-contained HTML report. This may take a moment...", textAlign = TextAlign.Center)
                        }
                    }
                    is ReportExportState.Progress -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.step / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, textAlign = TextAlign.Center)
                            Text("${state.step}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    is ReportExportState.Success -> {
                        Text("Interactive report exported successfully! Extract the ZIP file and open index.html to view.")
                    }
                    is ReportExportState.Error -> {
                        Text("Error: ${state.message}")
                    }
                    else -> {}
                }
            },
            confirmButton = {
                if (exportState !is ReportExportState.Loading && exportState !is ReportExportState.Progress) {
                    Button(onClick = { 
                        showExportDialog = false
                        viewModel.clearExportState()
                    }) {
                        Text("OK")
                    }
                }
            }
        )
    }
}

// Packages-visible helpers used across screens like ParticipantProfileScreen.kt
@Composable
fun InfoColumn(label: String, value: String, color: Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = RowDefaults.cardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class DashboardStats(
    val daysActive: Long = 1L,
    val mediaCount: Int = 0,
    val totalWords: Int = 0,
    val totalEmojis: Int = 0,
    val totalLinks: Int = 0,
    val avgMsgPerDay: Float = 0f,
    val activityScore: Int = 0,
    val weekdayMap: Map<Int, Int> = emptyMap(),
    val hourMap: Map<Int, Int> = emptyMap()
)
