package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TaskManager
import com.example.engine.TaskStatus
import com.example.engine.BackgroundTask
import com.example.ui.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCenterScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by TaskManager.tasks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { TaskManager.clearCompleted() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Completed")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No background tasks running", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks.reversed()) { task ->
                    TaskCard(task)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: BackgroundTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when(task.status) {
                TaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                TaskStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(task.name, fontWeight = FontWeight.Bold)
                when(task.status) {
                    TaskStatus.QUEUED -> Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.Gray)
                    TaskStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    TaskStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    TaskStatus.FAILED -> Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    TaskStatus.CANCELLED -> Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Gray)
                    TaskStatus.PAUSED -> Icon(Icons.Default.Pause, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                    TextButton(onClick = { TaskManager.cancelTask(task.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
