package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.ChatViewModel
import com.example.data.Workspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(
    viewModel: ChatViewModel,
    onNavigateToTaskCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workspaces = listOf(
        Workspace(1, "Personal Archive", "My exported personal chats"),
        Workspace(2, "Work Team", "Office communication logs", isPinned = true)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToTaskCenter) {
                        Icon(Icons.Default.TaskAlt, contentDescription = "Tasks")
                    }
                    IconButton(onClick = { /* Add Workspace */ }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Workspace")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Import Chat */ }) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import Chat")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Pinned Workspaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(workspaces.filter { it.isPinned }) { workspace ->
                WorkspaceCard(workspace)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Recent Workspaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(workspaces.filter { !it.isPinned }) { workspace ->
                WorkspaceCard(workspace)
            }
        }
    }
}

@Composable
fun WorkspaceCard(workspace: Workspace) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(workspace.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (workspace.description.isNotBlank()) {
                    Text(workspace.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (workspace.isPinned) {
                Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
