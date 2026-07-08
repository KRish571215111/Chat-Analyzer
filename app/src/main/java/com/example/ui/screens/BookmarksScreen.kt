package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val bookmarked by viewModel.bookmarkedMessages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarked Messages", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (bookmarked.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.BookmarkBorder,
                    title = "No Bookmarks Saved",
                    description = "Long press any message inside the Message Viewer or click the small bookmark tag to pin important records here for easy referencing.",
                    helpfulTip = "Bookmarks are synced locally for quick access."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookmarked) { msg ->
                        MessageItem(
                            message = msg,
                            searchQuery = "",
                            onLongClick = {},
                            onBookmarkToggle = { viewModel.toggleBookmark(msg.id, !msg.isBookmarked) },
                            onFavoriteToggle = { viewModel.toggleFavoriteMessage(msg.id, !msg.isPinned) }
                        )
                    }
                }
            }
        }
    }
}
