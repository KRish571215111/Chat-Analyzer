package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.ui.AiState
import com.example.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageViewerScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages = viewModel.filteredMessagesPaged.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterSender by viewModel.filterSender.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val participants by viewModel.currentParticipants.collectAsState()
    val aiState by viewModel.aiState.collectAsState()

    var showMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showAiExplanationDialog by remember { mutableStateOf<String?>(null) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val sendersList = remember(participants) {
        listOf("All Senders") + participants.map { it.name }
    }

    val typesList = listOf("All Messages", "TEXT", "IMAGE", "VIDEO", "AUDIO", "LOCATION", "CALL", "DELETED", "SYSTEM", "DOCUMENT", "POLL", "VOICE_NOTE")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search messages...") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            if (showAdvancedFilters) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.setFilterSender(null) }, modifier = Modifier.weight(1f)) { Text("Clear Sender") }
                    Button(onClick = { viewModel.setFilterType(null) }, modifier = Modifier.weight(1f)) { Text("Clear Type") }
                }
            }

            if (messages.itemCount == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).fillMaxWidth()) {
                    Text("No matching messages found", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.setSearchQuery("") }) {
                        Text("Clear Search")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.setFilterSender(null); viewModel.setFilterType(null) }) {
                        Text("Clear All Filters")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = messages.itemCount,
                        key = messages.itemKey { it.id },
                        contentType = messages.itemContentType { "ChatMessage" }
                    ) { index ->
                        val msg = messages[index]
                        if (msg != null) {
                            MessageItem(
                                message = msg,
                                searchQuery = searchQuery,
                                onLongClick = { showMenuMessage = msg },
                                onBookmarkToggle = { viewModel.toggleBookmark(msg.id, !msg.isBookmarked) },
                                onFavoriteToggle = { /* Not implemented */ }
                            )
                        }
                    }
                }
            }
        }

        showMenuMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { showMenuMessage = null },
                title = { Text("Message Actions") },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Copy Content") },
                            leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("WhatsApp Message", msg.messageText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                                showMenuMessage = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text(if (msg.isBookmarked) "Remove Bookmark" else "Bookmark Message") },
                            leadingContent = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.toggleBookmark(msg.id, !msg.isBookmarked)
                                showMenuMessage = null
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMenuMessage = null }) { Text("Dismiss") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessage,
    searchQuery: String,
    onLongClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }
    val dateStr = sdf.format(Date(message.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.senderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val formattedText = remember(message.messageText, searchQuery) {
                highlightSearch(message.messageText, searchQuery)
            }
            Text(
                text = formattedText,
                fontSize = 14.sp
            )
            if (message.isBookmarked) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Bookmarked", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

fun highlightSearch(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return androidx.compose.ui.text.AnnotatedString(text)
    
    return buildAnnotatedString {
        try {
            val pattern = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(query), java.util.regex.Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)
            var startIdx = 0
            while (matcher.find()) {
                append(text.substring(startIdx, matcher.start()))
                withStyle(SpanStyle(background = Color.Yellow, color = Color.Black)) {
                    append(text.substring(matcher.start(), matcher.end()))
                }
                startIdx = matcher.end()
            }
            if (startIdx < text.length) {
                append(text.substring(startIdx))
            }
        } catch (e: Exception) {
            append(text)
        }
    }
}
