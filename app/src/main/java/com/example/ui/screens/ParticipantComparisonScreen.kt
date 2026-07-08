package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Participant
import com.example.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantComparisonScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    onBack: () -> Unit
) {
    val participants by viewModel.currentParticipants.collectAsState()
    
    var participant1 by remember { mutableStateOf<Participant?>(null) }
    var participant2 by remember { mutableStateOf<Participant?>(null) }
    
    var showPicker1 by remember { mutableStateOf(false) }
    var showPicker2 by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare Participants") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Participant 1 Card
                ParticipantSelectorCard(
                    participant = participant1,
                    modifier = Modifier.weight(1f),
                    onClick = { showPicker1 = true }
                )
                
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Compare",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Participant 2 Card
                ParticipantSelectorCard(
                    participant = participant2,
                    modifier = Modifier.weight(1f),
                    onClick = { showPicker2 = true }
                )
            }
            
            if (participant1 != null && participant2 != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ComparisonStatRow("Messages Sent", participant1!!.messageCount, participant2!!.messageCount)
                    }
                    item {
                        ComparisonStatRow("Words Typed", participant1!!.wordCount, participant2!!.wordCount)
                    }
                    item {
                        ComparisonStatRow("Media Shared", participant1!!.mediaCount, participant2!!.mediaCount)
                    }
                    item {
                        ComparisonStatRow("Links Shared", participant1!!.linkCount, participant2!!.linkCount)
                    }
                    item {
                        ComparisonStatRow("Emojis Used", participant1!!.emojiCount, participant2!!.emojiCount)
                    }
                    item {
                        ComparisonTextRow("Favorite Emoji", participant1!!.favoriteEmoji ?: "-", participant2!!.favoriteEmoji ?: "-")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select two participants to compare their stats", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    
    if (showPicker1) {
        ParticipantPickerDialog(
            participants = participants.filter { it.id != participant2?.id },
            onDismiss = { showPicker1 = false },
            onSelect = { participant1 = it; showPicker1 = false }
        )
    }
    
    if (showPicker2) {
        ParticipantPickerDialog(
            participants = participants.filter { it.id != participant1?.id },
            onDismiss = { showPicker2 = false },
            onSelect = { participant2 = it; showPicker2 = false }
        )
    }
}

@Composable
fun ParticipantSelectorCard(participant: Participant?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(100.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (participant == null) {
                Text("Select", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(participant.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(participant.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ComparisonStatRow(label: String, val1: Int, val2: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$val1", fontWeight = FontWeight.Bold, color = if (val1 >= val2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text("$val2", fontWeight = FontWeight.Bold, color = if (val2 >= val1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                val total = (val1 + val2).coerceAtLeast(1)
                val weight1 = val1.toFloat() / total
                val weight2 = val2.toFloat() / total
                if (weight1 > 0) {
                    Box(modifier = Modifier.weight(weight1).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                }
                if (weight2 > 0) {
                    Box(modifier = Modifier.weight(weight2).fillMaxHeight().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                }
            }
        }
    }
}

@Composable
fun ComparisonTextRow(label: String, val1: String, val2: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(val1, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Text(val2, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantPickerDialog(participants: List<Participant>, onDismiss: () -> Unit, onSelect: (Participant) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Participant") },
        text = {
            LazyColumn {
                items(participants.size) { index ->
                    val participant = participants[index]
                    ListItem(
                        headlineContent = { Text(participant.name) },
                        modifier = Modifier.clickable { onSelect(participant) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
