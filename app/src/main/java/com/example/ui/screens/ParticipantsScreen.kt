package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Participant
import com.example.ui.ChatViewModel
import com.example.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val session by viewModel.currentSession.collectAsState()
    val participants by viewModel.currentParticipants.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Top Contributors") }
    var isGridView by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Calculate dynamic ranks and percentages based on total non-system messages
    val totalMsgCount = remember(messages) {
        messages.count { !it.isSystem }.coerceAtLeast(1)
    }

    val processedParticipants = remember(participants, messages, searchQuery, sortBy) {
        var list = participants.mapIndexed { index, part ->
            val percentage = (part.messageCount.toFloat() / totalMsgCount) * 100f
            val rank = index + 1 // raw list is sorted by contribution initially
            val activityScore = (part.messageCount * 1.2f + part.wordCount * 0.1f + part.mediaCount * 2f).toInt()
            ParticipantUiItem(
                participant = part,
                percentage = percentage,
                rank = rank,
                activityScore = activityScore
            )
        }

        // Apply Search Filter
        if (searchQuery.isNotBlank()) {
            val normalizedQuery = searchQuery.replace(Regex("[^\\d+]"), "")
            list = list.filter {
                it.participant.name.contains(searchQuery, ignoreCase = true) ||
                        (it.participant.phone?.contains(searchQuery) ?: false) ||
                        (normalizedQuery.isNotEmpty() && it.participant.normalizedPhone?.contains(normalizedQuery) == true)
            }
        }

        // Apply Sorting
        list = when (sortBy) {
            "Top Contributors" -> list.sortedByDescending { it.participant.messageCount }
            "Least Contributors" -> list.sortedBy { it.participant.messageCount }
            "Most Messages" -> list.sortedByDescending { it.participant.messageCount }
            "Least Messages" -> list.sortedBy { it.participant.messageCount }
            "Most Media" -> list.sortedByDescending { it.participant.mediaCount }
            "Most Images" -> list.sortedByDescending { it.participant.imagesShared }
            "Most Videos" -> list.sortedByDescending { it.participant.videosShared }
            "Most Documents" -> list.sortedByDescending { it.participant.documentsShared }
            "Most Links" -> list.sortedByDescending { it.participant.linkCount }
            "Most Audio" -> list.sortedByDescending { it.participant.audioShared + it.participant.voiceNotesShared }
            "Most Stickers" -> list.sortedByDescending { it.participant.stickersShared }
            "Most Words" -> list.sortedByDescending { it.participant.wordCount }
            "Most Characters" -> list.sortedByDescending { it.participant.characterCount }
            "Alphabetical" -> list.sortedBy { it.participant.name.lowercase() }
            "Silent Members" -> list.filter { it.participant.messageCount == 0 }
            else -> list.sortedByDescending { it.participant.messageCount }
        }

        list
    }

    val activeCount = participants.count { it.messageCount > 0 }
    val inactiveCount = participants.size - activeCount
    val avgMessages = if (participants.isNotEmpty()) participants.map { it.messageCount }.average().toInt() else 0
    val mostActive = participants.maxByOrNull { it.messageCount }?.name ?: "None"
    val leastActive = participants.minByOrNull { it.messageCount }?.name ?: "None"
    val avgScore = if (processedParticipants.isNotEmpty()) processedParticipants.map { it.activityScore }.average().toInt() else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session?.name ?: "Members Analytics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "${participants.size} total • $activeCount active",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.ParticipantComparison(sessionId)) },
                        modifier = Modifier.testTag("compare_participants_button")
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = "Compare Participants")
                    }
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("layout_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List View"
                        )
                    }
                    var showExportMenu by remember { mutableStateOf(false) }
                    var selectedFormat by remember { mutableStateOf("") }
                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("*/*")
                    ) { uri ->
                        if (uri != null) {
                            coroutineScope.launch {
                                try {
                                    com.example.parser.MemberExportEngine.exportParticipants(
                                        context = context,
                                        uri = uri,
                                        format = selectedFormat,
                                        participants = participants
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.IosShare, contentDescription = "Export")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            listOf("CSV", "VCF", "JSON", "TXT", "HTML").forEach { fmt ->
                                DropdownMenuItem(
                                    text = { Text("Export as $fmt") },
                                    onClick = { 
                                        showExportMenu = false
                                        selectedFormat = fmt
                                        val ext = fmt.lowercase()
                                        val mime = when (fmt) {
                                            "CSV" -> "text/csv"
                                            "VCF" -> "text/vcard"
                                            "JSON" -> "application/json"
                                            "TXT" -> "text/plain"
                                            "HTML" -> "text/html"
                                            else -> "*/*"
                                        }
                                        // We use */* to ensure all devices show the picker, but suggest a filename
                                        exportLauncher.launch("members.$ext")
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Options")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortOptions = listOf(
                                "Top Contributors", "Least Contributors",
                                "Most Messages", "Least Messages",
                                "Most Media", "Most Images", "Most Videos", 
                                "Most Documents", "Most Links", "Most Audio", 
                                "Most Stickers", "Most Words", "Most Characters", 
                                "Alphabetical", "Silent Members"
                            )
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        sortBy = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortBy == option) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.MemberImport(sessionId)) },
                icon = { Icon(Icons.Default.GroupAdd, contentDescription = "Import") },
                text = { Text("Import") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Stats Cards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { StatBadge("Active", activeCount.toString()) }
                item { StatBadge("Inactive", inactiveCount.toString()) }
                item { StatBadge("Avg Msgs", avgMessages.toString()) }
                item { StatBadge("Avg Score", avgScore.toString()) }
                item { StatBadge("Most Active", mostActive) }
                item { StatBadge("Least Active", leastActive) }
            }

            // Search Bar & Filter Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or phone...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("participant_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                // Sort indicator badge
                SuggestionChip(
                    onClick = { showSortMenu = true },
                    label = { Text(sortBy, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("sort_indicator_chip")
                )
            }

            if (processedParticipants.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.PersonSearch,
                        title = "No Participants Found",
                        description = "No participants match your search criteria.",
                        helpfulTip = "Try adjusting your search terms or filters."
                    )
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("participants_grid")
                    ) {
                        items(processedParticipants) { item ->
                            ParticipantGridCard(
                                item = item,
                                onClick = {
                                    viewModel.navigateTo(Screen.ParticipantProfile(sessionId, item.participant.id))
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("participants_list")
                    ) {
                        itemsIndexed(processedParticipants) { index, item ->
                            ParticipantListCard(
                                item = item,
                                rankPosition = index + 1,
                                onClick = {
                                    viewModel.navigateTo(Screen.ParticipantProfile(sessionId, item.participant.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantListCard(
    item: ParticipantUiItem,
    rankPosition: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("participant_card_${item.participant.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when (rankPosition) {
                            1 -> Color(0xFFFFD700) // Gold
                            2 -> Color(0xFFC0C0C0) // Silver
                            3 -> Color(0xFFCD7F32) // Bronze
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rankPosition.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (rankPosition <= 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Avatar circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.participant.name.take(2).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.participant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.participant.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (item.participant.phone != null && item.participant.phone != item.participant.name) {
                    Text(
                        text = item.participant.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.participant.messageCount == 0) {
                    Text(
                        text = "Status: No messages found in this exported chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text("${item.participant.messageCount} msgs", modifier = Modifier.padding(2.dp))
                    }
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text(String.format("%.1f%%", item.percentage), modifier = Modifier.padding(2.dp))
                    }
                    item.participant.favoriteEmoji?.let { emoji ->
                        Text(emoji, fontSize = 12.sp)
                    }
                }
            }

            // Score KPI
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.activityScore.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ParticipantGridCard(
    item: ParticipantUiItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("participant_grid_card_${item.participant.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank text
                Text(
                    text = "#${item.rank}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )

                if (item.participant.isFavorite) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Pinned",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.participant.name.take(2).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Name
            Text(
                text = item.participant.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )

            if (item.participant.phone != null && item.participant.phone != item.participant.name) {
                Text(
                    text = item.participant.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.participant.messageCount == 0) {
                Text(
                    text = "Status: No messages found in this exported chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            item.participant.favoriteEmoji?.let { emoji ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("Fav: $emoji", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        item.participant.messageCount.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Share",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format("%.1f%%", item.percentage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatBadge(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class ParticipantUiItem(
    val participant: Participant,
    val percentage: Float,
    val rank: Int,
    val activityScore: Int
)
