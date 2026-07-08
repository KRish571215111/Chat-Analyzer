package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ChatMessage
import com.example.data.MediaFile
import com.example.ui.ChatViewModel
import com.example.ui.ImageAnalysisState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// --- Models ---
sealed class UnifiedAsset {
    abstract val id: Long
    abstract val sender: String
    abstract val timestamp: Long
    abstract val size: Long
    abstract val type: String
    abstract val title: String
    abstract val associatedMessage: ChatMessage?
    abstract val isBookmarked: Boolean
    abstract val isPinned: Boolean

    data class Media(
        val mediaFile: MediaFile, 
        val msg: ChatMessage?
    ) : UnifiedAsset() {
        override val id = mediaFile.id
        override val sender = mediaFile.senderName
        override val timestamp = mediaFile.timestamp
        override val size = mediaFile.fileSize
        override val type = mediaFile.fileType.lowercase()
        override val title = mediaFile.fileName
        override val associatedMessage = msg
        override val isBookmarked = msg?.isBookmarked == true
        override val isPinned = msg?.isPinned == true
        
        val extension = mediaFile.fileName.substringAfterLast('.', "").lowercase()
        val isPdf = extension == "pdf"
        val isWord = extension in listOf("doc", "docx")
        val isExcel = extension in listOf("xls", "xlsx", "csv")
        val isPowerPoint = extension in listOf("ppt", "pptx")
        val isArchive = extension in listOf("zip", "rar", "7z", "tar", "gz")
        val isApk = extension == "apk"
    }

    data class MsgAsset(
        val msg: ChatMessage,
        val assetType: String,
        val extraData: String = ""
    ) : UnifiedAsset() {
        override val id = msg.id + (extraData.hashCode().toLong() shl 32)
        override val sender = msg.senderName
        override val timestamp = msg.timestamp
        override val size = 0L
        override val type = assetType
        override val title = if (assetType == "link") extraData else msg.messageText.take(50)
        override val associatedMessage = msg
        override val isBookmarked = msg.isBookmarked
        override val isPinned = msg.isPinned
    }
}

sealed class GalleryMode {
    object Dashboard : GalleryMode()
    data class Category(val categoryName: String) : GalleryMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    viewModel: ChatViewModel,
    sessionId: Long,
    modifier: Modifier = Modifier
) {
    val mediaFiles by viewModel.currentMediaFiles.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val participants by viewModel.currentParticipants.collectAsState()

    var currentMode by remember { mutableStateOf<GalleryMode>(GalleryMode.Dashboard) }
    var activeAsset by remember { mutableStateOf<UnifiedAsset?>(null) }
    var activeMediaForAnalysis by remember { mutableStateOf<MediaFile?>(null) }

    // Build Unified Assets
    val unifiedAssets = remember(mediaFiles, messages) {
        val assets = mutableListOf<UnifiedAsset>()
        val mediaMessages = messages.filter { it.isMedia }
        
        mediaFiles.forEach { file ->
            val cleanName = file.fileName.substringBeforeLast(".")
            val msg = mediaMessages.firstOrNull { it.messageText.contains(cleanName, ignoreCase = true) }
            assets.add(UnifiedAsset.Media(file, msg))
        }
        
        val urlPattern = Pattern.compile("\\b(https?|ftp|file)://([-a-zA-Z0-9+&@#/%?=~_|!:,.;]*)[-a-zA-Z0-9+&@#/%=~_|]", Pattern.CASE_INSENSITIVE)
        
        messages.forEach { msg ->
            when (msg.messageType) {
                "LOCATION" -> assets.add(UnifiedAsset.MsgAsset(msg, "location"))
                "CONTACT" -> assets.add(UnifiedAsset.MsgAsset(msg, "contact"))
                "POLL" -> assets.add(UnifiedAsset.MsgAsset(msg, "poll"))
                "DELETED" -> assets.add(UnifiedAsset.MsgAsset(msg, "deleted"))
                "LINK", "TEXT" -> {
                    val matcher = urlPattern.matcher(msg.messageText)
                    while(matcher.find()) {
                        matcher.group(0)?.let { url ->
                            assets.add(UnifiedAsset.MsgAsset(msg, "link", url))
                        }
                    }
                }
            }
        }
        assets
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (val mode = currentMode) {
                            is GalleryMode.Dashboard -> "Digital Asset Manager"
                            is GalleryMode.Category -> mode.categoryName
                        }, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (currentMode is GalleryMode.Category) {
                            currentMode = GalleryMode.Dashboard
                        } else {
                            viewModel.navigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentMode is GalleryMode.Category) {
                        IconButton(onClick = { /* Export */ }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            Crossfade(targetState = currentMode, label = "Mode Switch") { mode ->
                when (mode) {
                    is GalleryMode.Dashboard -> DashboardMode(
                        assets = unifiedAssets,
                        onCategoryClick = { currentMode = GalleryMode.Category(it) }
                    )
                    is GalleryMode.Category -> CategoryMode(
                        categoryName = mode.categoryName,
                        assets = unifiedAssets,
                        participants = participants.map { it.name },
                        onAssetClick = { activeAsset = it },
                        onAnalyzeClick = { 
                            if (it is UnifiedAsset.Media) {
                                activeMediaForAnalysis = it.mediaFile
                            }
                        }
                    )
                }
            }
        }
    }

    // Asset Detail Dialog
    activeAsset?.let { asset ->
        AssetDetailDialog(
            asset = asset,
            onDismiss = { activeAsset = null },
            viewModel = viewModel
        )
    }

    // Vision Analysis Overlay Dialog
    activeMediaForAnalysis?.let { mediaFile ->
        VisionAnalysisDialog(
            mediaFile = mediaFile,
            viewModel = viewModel,
            onDismiss = {
                viewModel.clearImageAnalysisState()
                activeMediaForAnalysis = null
            }
        )
    }
}

// --- Dashboard Component ---
@Composable
fun DashboardMode(
    assets: List<UnifiedAsset>,
    onCategoryClick: (String) -> Unit
) {
    val totalMedia = assets.size
    val totalSize = assets.sumOf { it.size }
    
    val imgCount = assets.count { it.type == "image" }
    val vidCount = assets.count { it.type == "video" }
    val gifCount = assets.count { it.type == "gif" }
    val stickerCount = assets.count { it.type == "sticker" }
    val vnCount = assets.count { it.type == "voice_note" || it.type == "voice" }
    val audCount = assets.count { it.type == "audio" }
    val docCount = assets.count { it.type == "document" || it is UnifiedAsset.Media && (it.isPdf || it.isWord || it.isExcel || it.isPowerPoint || it.isArchive || it.isApk) }
    
    val pdfCount = assets.count { it is UnifiedAsset.Media && it.isPdf }
    val wordCount = assets.count { it is UnifiedAsset.Media && it.isWord }
    val excelCount = assets.count { it is UnifiedAsset.Media && it.isExcel }
    val pptCount = assets.count { it is UnifiedAsset.Media && it.isPowerPoint }
    val zipCount = assets.count { it is UnifiedAsset.Media && it.isArchive }
    val apkCount = assets.count { it is UnifiedAsset.Media && it.isApk }
    
    val contactCount = assets.count { it.type == "contact" }
    val locCount = assets.count { it.type == "location" }
    val linkCount = assets.count { it.type == "link" }
    val pollCount = assets.count { it.type == "poll" }
    val deletedCount = assets.count { it.type == "deleted" }
    
    val knownTypes = listOf("image", "video", "gif", "sticker", "voice_note", "voice", "audio", "document", "contact", "location", "link", "poll", "deleted")
    val unknownCount = assets.count { it.type !in knownTypes && !(it is UnifiedAsset.Media && (it.isPdf || it.isWord || it.isExcel || it.isPowerPoint || it.isArchive || it.isApk)) }
    
    val missingCount = assets.count { it is UnifiedAsset.Media && !File(it.mediaFile.filePath).exists() }

    val formattedSize = formatSize(totalSize)
    val avgSize = if (totalMedia > 0) formatSize(totalSize / totalMedia) else "0 B"
    val largest = assets.maxByOrNull { it.size }?.let { formatSize(it.size) } ?: "0 B"
    
    // Calculate oldest and newest based on timestamp
    val newest = assets.filter { it.timestamp > 0 }.maxByOrNull { it.timestamp }
    val oldest = assets.filter { it.timestamp > 0 }.minByOrNull { it.timestamp }
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    val newestStr = newest?.let { sdf.format(Date(it.timestamp)) } ?: "N/A"
    val oldestStr = oldest?.let { sdf.format(Date(it.timestamp)) } ?: "N/A"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Main Banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Managed Assets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "$totalMedia Assets",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatText("Storage", formattedSize)
                        StatText("Avg Size", avgSize)
                        StatText("Largest", largest)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatText("Oldest", oldestStr)
                        StatText("Newest", newestStr)
                        StatText("Missing", "$missingCount")
                    }
                }
            }
        }
        
        item {
            Text("Asset Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        val fullList = listOf(
            Triple("Images", imgCount, Icons.Default.Image),
            Triple("Videos", vidCount, Icons.Default.Videocam),
            Triple("Documents", docCount, Icons.AutoMirrored.Filled.InsertDriveFile),
            Triple("Links", linkCount, Icons.Default.Link),
            Triple("Voice Notes", vnCount, Icons.Default.Mic),
            Triple("Audio Files", audCount, Icons.Default.MusicNote),
            Triple("GIFs", gifCount, Icons.Default.Gif),
            Triple("Stickers", stickerCount, Icons.AutoMirrored.Filled.StickyNote2),
            Triple("Locations", locCount, Icons.Default.LocationOn),
            Triple("Contacts", contactCount, Icons.Default.Person),
            Triple("Polls", pollCount, Icons.Default.Poll),
            Triple("PDF Files", pdfCount, Icons.Default.PictureAsPdf),
            Triple("Word Documents", wordCount, Icons.Default.Description),
            Triple("Excel Files", excelCount, Icons.Default.TableChart),
            Triple("PowerPoint Files", pptCount, Icons.Default.Slideshow),
            Triple("ZIP Archives", zipCount, Icons.Default.FolderZip),
            Triple("APK Files", apkCount, Icons.Default.Android),
            Triple("Deleted Placeholders", deletedCount, Icons.Default.Delete),
            Triple("Unknown Files", unknownCount, Icons.Default.QuestionMark)
        )

        items(fullList.chunked(2)) { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (name, count, icon) ->
                    Card(
                        modifier = Modifier.weight(1f).clickable { onCategoryClick(name) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatText(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// --- Category Component ---
@Composable
fun CategoryMode(
    categoryName: String,
    assets: List<UnifiedAsset>,
    participants: List<String>,
    onAssetClick: (UnifiedAsset) -> Unit,
    onAnalyzeClick: (UnifiedAsset) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSender by remember { mutableStateOf("All Senders") }
    var sortBy by remember { mutableStateOf("Newest") }
    var isGridView by remember { mutableStateOf(true) }

    val filteredAssets = remember(categoryName, assets, searchQuery, selectedSender, sortBy) {
        var result = assets.filter { asset ->
            when (categoryName) {
                "Images" -> asset.type == "image"
                "Videos" -> asset.type == "video"
                "Documents" -> asset.type == "document" || (asset is UnifiedAsset.Media && (asset.isPdf || asset.isWord || asset.isExcel || asset.isPowerPoint || asset.isArchive || asset.isApk))
                "Links" -> asset.type == "link"
                "Voice Notes" -> asset.type == "voice_note" || asset.type == "voice"
                "Audio Files" -> asset.type == "audio"
                "GIFs" -> asset.type == "gif"
                "Stickers" -> asset.type == "sticker"
                "Locations" -> asset.type == "location"
                "Contacts" -> asset.type == "contact"
                "Polls" -> asset.type == "poll"
                "PDF Files" -> asset is UnifiedAsset.Media && asset.isPdf
                "Word Documents" -> asset is UnifiedAsset.Media && asset.isWord
                "Excel Files" -> asset is UnifiedAsset.Media && asset.isExcel
                "PowerPoint Files" -> asset is UnifiedAsset.Media && asset.isPowerPoint
                "ZIP Archives" -> asset is UnifiedAsset.Media && asset.isArchive
                "APK Files" -> asset is UnifiedAsset.Media && asset.isApk
                "Deleted Placeholders" -> asset.type == "deleted"
                "Unknown Files" -> {
                    val knownTypes = listOf("image", "video", "gif", "sticker", "voice_note", "voice", "audio", "document", "contact", "location", "link", "poll", "deleted")
                    asset.type !in knownTypes && !(asset is UnifiedAsset.Media && (asset.isPdf || asset.isWord || asset.isExcel || asset.isPowerPoint || asset.isArchive || asset.isApk))
                }
                else -> true
            }
        }

        if (selectedSender != "All Senders") {
            result = result.filter { it.sender == selectedSender }
        }

        if (searchQuery.isNotBlank()) {
            result = result.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        when (sortBy) {
            "Newest" -> result.sortedByDescending { it.timestamp }
            "Oldest" -> result.sortedBy { it.timestamp }
            "Largest" -> result.sortedByDescending { it.size }
            "Smallest" -> result.sortedBy { it.size }
            "A-Z" -> result.sortedBy { it.title }
            else -> result
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = { Text("Search...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )

            // View toggle
            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = "Toggle View")
            }
        }
        
        // Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sender Dropdown
            var expandedSender by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = selectedSender != "All Senders",
                    onClick = { expandedSender = true },
                    label = { Text(selectedSender, overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 12.sp) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expandedSender, onDismissRequest = { expandedSender = false }) {
                    DropdownMenuItem(text = { Text("All Senders") }, onClick = { selectedSender = "All Senders"; expandedSender = false })
                    participants.forEach { part ->
                        DropdownMenuItem(text = { Text(part) }, onClick = { selectedSender = part; expandedSender = false })
                    }
                }
            }

            // Sort Dropdown
            var expandedSort by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    selected = sortBy != "Newest",
                    onClick = { expandedSort = true },
                    label = { Text(sortBy, overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 12.sp) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = expandedSort, onDismissRequest = { expandedSort = false }) {
                    listOf("Newest", "Oldest", "Largest", "Smallest", "A-Z").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortBy = option; expandedSort = false })
                    }
                }
            }
        }

        if (filteredAssets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                EmptyStateView(
                    icon = Icons.Default.HideImage,
                    title = "No Media Found",
                    description = "There are no media assets matching the selected filters.",
                    helpfulTip = "Try changing the category or clear your search terms."
                )
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredAssets) { asset ->
                        AssetGridCard(asset, onClick = { onAssetClick(asset) })
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredAssets) { asset ->
                        AssetListCard(asset, onClick = { onAssetClick(asset) })
                    }
                }
            }
        }
    }
}

@Composable
fun AssetGridCard(asset: UnifiedAsset, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (asset is UnifiedAsset.Media) {
                val exists = File(asset.mediaFile.filePath).exists()
                if (exists && (asset.type == "image" || asset.type == "video")) {
                    if (asset.type == "image") {
                        AsyncImage(
                            model = File(asset.mediaFile.filePath),
                            contentDescription = asset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.PlayCircleFilled, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp), tint = Color.Gray)
                    }
                } else if (!exists) {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Red)
                        Text("Missing", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val icon = when (asset.type) {
                        "document" -> Icons.AutoMirrored.Filled.InsertDriveFile
                        "voice_note", "audio", "voice" -> Icons.Default.Mic
                        else -> Icons.Default.Folder
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp), tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                val icon = when (asset.type) {
                    "link" -> Icons.Default.Link
                    "location" -> Icons.Default.LocationOn
                    "contact" -> Icons.Default.Person
                    "poll" -> Icons.Default.Poll
                    "deleted" -> Icons.Default.Delete
                    else -> Icons.Default.Folder
                }
                Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            
            // Text overlay for non-images
            if (!(asset is UnifiedAsset.Media && asset.type == "image" && File(asset.mediaFile.filePath).exists())) {
                Text(
                    asset.title,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(4.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun AssetListCard(asset: UnifiedAsset, onClick: () -> Unit) {
    val dateText = remember(asset.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(asset.timestamp))
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (asset is UnifiedAsset.Media) {
                    val exists = File(asset.mediaFile.filePath).exists()
                    if (exists && (asset.type == "image" || asset.type == "video")) {
                        if (asset.type == "image") {
                            AsyncImage(
                                model = File(asset.mediaFile.filePath),
                                contentDescription = asset.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.Gray)
                        }
                    } else if (!exists) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                            Text("Missing", color = Color.Red, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val icon = when (asset.type) {
                            "document" -> Icons.AutoMirrored.Filled.InsertDriveFile
                            "voice_note", "audio", "voice" -> Icons.Default.Mic
                            else -> Icons.Default.Folder
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val icon = when (asset.type) {
                        "link" -> Icons.Default.Link
                        "location" -> Icons.Default.LocationOn
                        "contact" -> Icons.Default.Person
                        "poll" -> Icons.Default.Poll
                        "deleted" -> Icons.Default.Delete
                        else -> Icons.Default.Folder
                    }
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(asset.sender, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(dateText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            if (asset.size > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatSize(asset.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// --- Detail Dialog ---
@Composable
fun AssetDetailDialog(
    asset: UnifiedAsset,
    onDismiss: () -> Unit,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Asset Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (asset is UnifiedAsset.Media && asset.type == "image") {
                        val exists = File(asset.mediaFile.filePath).exists()
                        if (exists) {
                            AsyncImage(
                                model = File(asset.mediaFile.filePath),
                                contentDescription = asset.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("Image not found on device", color = Color.Gray)
                        }
                    } else if (asset.type == "link") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(asset.title, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { 
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(asset.title))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }) {
                                Text("Open Link")
                            }
                        }
                    } else {
                        // Generic placeholder
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Text(asset.title, modifier = Modifier.padding(top = 80.dp, start = 16.dp, end = 16.dp), textAlign = TextAlign.Center)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Metadata
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow("Name", asset.title)
                        DetailRow("Type", asset.type.uppercase())
                        DetailRow("Sender", asset.sender)
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(asset.timestamp))
                        DetailRow("Date", dateStr)
                        if (asset.size > 0) {
                            DetailRow("Size", formatSize(asset.size))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { /* Export */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export")
                    }
                    if (asset is UnifiedAsset.Media && asset.type == "image") {
                        Button(
                            onClick = { 
                                viewModel.requestAiConsent("Describe this asset", asset.mediaFile)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Analyze")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}


@Composable
fun VisionAnalysisDialog(
    mediaFile: MediaFile,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val analysisState by viewModel.imageAnalysisState.collectAsState()
    var promptInput by remember { mutableStateOf("Describe this asset and categorize it (is it a screenshot, meme, chart, receipt, or QR code?)") }

    val exists = remember(mediaFile.filePath) { File(mediaFile.filePath).exists() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("vision_analysis_dialog"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "GLM 4.7 & Gemini AI Vision",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            mediaFile.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaFile.fileType.lowercase() == "image" && exists) {
                            AsyncImage(
                                model = File(mediaFile.filePath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (mediaFile.fileType.lowercase() == "image") Icons.Default.BrokenImage else Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (mediaFile.fileType.lowercase() == "image") "Physical image not present on-device" else "Document Preview Not Available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Metadata Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sender", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(mediaFile.senderName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Size", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            val kb = String.format("%.1f KB", mediaFile.fileSize.toDouble() / 1024)
                            Text(kb, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider()

                    // Predefined Prompt Buttons
                    if (mediaFile.fileType.lowercase() == "image" && exists) {
                        Text(
                            "Preset Vision Prompts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "OCR" to "Perform OCR transcription",
                                "Meme" to "Is this a meme or joke?",
                                "Layout" to "Analyze visual structure"
                            ).forEach { (label, pr) ->
                                OutlinedButton(
                                    onClick = { promptInput = pr },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Custom Input Text Area
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .testTag("vision_prompt_input"),
                        label = { Text("Vision Model Prompt") },
                        maxLines = 3
                    )

                    // Execute Vision Button
                    Button(
                        onClick = { viewModel.requestAiConsent(promptInput, mediaFile) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_vision_button"),
                        enabled = analysisState !is ImageAnalysisState.Loading && mediaFile.fileType.lowercase() == "image" && exists
                    ) {
                        if (analysisState is ImageAnalysisState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze with GLM 4.7 Flash / Gemini", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Response Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "AI Analysis Results",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            when (val state = analysisState) {
                                is ImageAnalysisState.Loading -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Transcribing layout with deep vision nodes...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                is ImageAnalysisState.Success -> {
                                    Text(
                                        state.caption,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                is ImageAnalysisState.Error -> {
                                    Text(
                                        state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                else -> {
                                    val dbCap = mediaFile.aiCaption
                                    if (!dbCap.isNullOrBlank()) {
                                        Text(dbCap, style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text(
                                            "Submit a vision prompt to run GLM 4.7 Flash image analysis.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatSize(size: Long): String {
    return when {
        size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size.toDouble() / (1024 * 1024 * 1024))
        size >= 1024 * 1024 -> String.format("%.2f MB", size.toDouble() / (1024 * 1024))
        size >= 1024 -> String.format("%.2f KB", size.toDouble() / 1024)
        else -> "$size B"
    }
}
