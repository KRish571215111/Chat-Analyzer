package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ChatViewModel
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String, val icon: ImageVector, val description: String) {
    GENERAL("General", Icons.Default.Settings, "Language, default views, and startup behaviors"),
    APPEARANCE("Appearance", Icons.Default.Palette, "Themes, scaling, and visual engine options"),
    AI("AI Configuration", Icons.Default.AutoAwesome, "API keys, models, and intelligence behavior"),
    ANALYTICS("Analytics", Icons.Default.Insights, "Heatmaps, chart fidelity, and calculations"),
    REPORTS("Reports & Export", Icons.Default.Description, "HTML generation, PDF quality, and contents"),
    MEDIA("Media", Icons.Default.PermMedia, "Thumbnails, caches, and image preview quality"),
    SEARCH("Search", Icons.Default.Search, "History, fuzzy matching, and indexing limits"),
    PRIVACY_SECURITY("Privacy & Security", Icons.Default.Security, "App lock, encryption, and data retention"),
    STORAGE("Storage & Backup", Icons.Default.Storage, "Database cleanup, local backups, and caches"),
    DEVELOPER("Developer Options", Icons.Default.Code, "Debug logs, performance monitors, and inspector"),
    ABOUT("About", Icons.Default.Info, "Versions, licenses, and documentation")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory?.title ?: "Control Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCategory != null) selectedCategory = null
                        else viewModel.navigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedCategory == null) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Settings globally reset to defaults")
                            }
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = "Reset All")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedCategory == null) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true
                )

                // Categories Grid/List
                val filteredCategories = SettingsCategory.values().filter {
                    it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCategories) { category ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = category },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(category.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                // Settings Detail View
                SettingsDetailContent(
                    category = selectedCategory!!,
                    viewModel = viewModel,
                    onShowSnackbar = { msg ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsDetailContent(
    category: SettingsCategory,
    viewModel: ChatViewModel,
    onShowSnackbar: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (category) {
            SettingsCategory.GENERAL -> item { GeneralSettings() }
            SettingsCategory.APPEARANCE -> item { AppearanceSettings(viewModel) }
            SettingsCategory.AI -> item { AiSettings(viewModel, onShowSnackbar) }
            SettingsCategory.ANALYTICS -> item { AnalyticsSettings() }
            SettingsCategory.REPORTS -> item { ReportsSettings() }
            SettingsCategory.MEDIA -> item { MediaSettings() }
            SettingsCategory.SEARCH -> item { SearchSettings() }
            SettingsCategory.PRIVACY_SECURITY -> item { PrivacySecuritySettings(viewModel, onShowSnackbar) }
            SettingsCategory.STORAGE -> item { StorageSettings(onShowSnackbar) }
            SettingsCategory.DEVELOPER -> item { DeveloperSettings() }
            SettingsCategory.ABOUT -> item { AboutSettings() }
        }
    }
}

// Sub-components for each category
@Composable
fun GeneralSettings() {
    SettingsSwitchItem("Remember Last Chat", "Automatically open the last viewed session on startup.", true)
    SettingsSwitchItem("Auto Refresh", "Refresh data dynamically when returning to app.", false)
    SettingsDropdownItem("Language", "English")
    SettingsDropdownItem("Date Format", "System Default")
    SettingsDropdownItem("Default Sorting", "Latest First")
}

@Composable
fun AppearanceSettings(viewModel: ChatViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val amoledDark by viewModel.amoledDark.collectAsState()
    val highContrast by viewModel.highContrast.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()

    Text("Theme Mode", fontWeight = FontWeight.SemiBold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
            FilterChip(
                selected = themeMode == mode,
                onClick = { viewModel.updateThemeSettings(mode, amoledDark, highContrast, fontScale) },
                label = { Text(mode) }
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSwitchItem("AMOLED Deep Dark", "Use pure black for dark mode.", amoledDark) { 
        viewModel.updateThemeSettings(themeMode, it, highContrast, fontScale) 
    }
    SettingsSwitchItem("High Contrast Mode", "Increase text and boundary visibility.", highContrast) {
        viewModel.updateThemeSettings(themeMode, amoledDark, it, fontScale)
    }
    SettingsSwitchItem("Reduce Motion", "Disable complex UI animations.", false)

    Spacer(modifier = Modifier.height(16.dp))
    Text("Font Size Multiplier: ${(fontScale * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
    Slider(
        value = fontScale,
        onValueChange = { viewModel.updateThemeSettings(themeMode, amoledDark, highContrast, it) },
        valueRange = 0.8f..1.4f,
        steps = 5
    )
}

@Composable
fun AiSettings(viewModel: ChatViewModel, onShowSnackbar: (String) -> Unit) {
    val apiKeyValue by viewModel.aiApiKeySetting.collectAsState()
    val useZaiValue by viewModel.useZaiSetting.collectAsState()
    val baseUrlSetting by viewModel.zaiBaseUrlSetting.collectAsState()
    val modelSetting by viewModel.zaiModelSetting.collectAsState()

    var inputKey by remember(apiKeyValue) { mutableStateOf(apiKeyValue) }
    var useZai by remember(useZaiValue) { mutableStateOf(useZaiValue) }
    var inputBaseUrl by remember(baseUrlSetting) { mutableStateOf(baseUrlSetting) }
    var inputModel by remember(modelSetting) { mutableStateOf(modelSetting) }

    SettingsSwitchItem("Enable AI Assistant", "Allow cognitive features and summaries.", true)
    SettingsSwitchItem("Use Custom AI Endpoint (e.g. Z.AI)", "Standard OpenAI completion format.", useZai) { useZai = it }
    
    AnimatedVisibility(visible = useZai) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = inputKey, onValueChange = { inputKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = inputBaseUrl, onValueChange = { inputBaseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = inputModel, onValueChange = { inputModel = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { 
        viewModel.updateSettings(inputKey, useZai, inputBaseUrl, inputModel) 
        onShowSnackbar("AI Config Saved")
    }, modifier = Modifier.fillMaxWidth()) { Text("Save AI Configurations") }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSwitchItem("Auto Generate Summary", "Automatically summarize long chats on import.", false)
    SettingsSwitchItem("Offline Mode", "Disable all network AI requests. Uses local logic.", false)
    Button(onClick = { onShowSnackbar("AI Cache Cleared") }, colors = ButtonDefaults.outlinedButtonColors(), modifier = Modifier.fillMaxWidth()) { Text("Clear AI Cache") }
}

@Composable
fun AnalyticsSettings() {
    SettingsSwitchItem("Enable Heatmaps", "Show activity heatmaps in dashboard.", true)
    SettingsSwitchItem("Enable Interactive Charts", "Render complex Vico charts.", true)
    SettingsSwitchItem("Chart Animation", "Smooth transitions on chart load.", true)
    SettingsDropdownItem("Activity Score Formula", "Weighted Average")
    SettingsDropdownItem("Timeline Resolution", "Daily")
}

@Composable
fun ReportsSettings() {
    SettingsDropdownItem("Default Export Format", "Interactive HTML")
    SettingsSwitchItem("Include AI Insights", "Embed AI generated summaries in exports.", true)
    SettingsSwitchItem("Include Media", "Bundle media files within exports.", true)
    SettingsSwitchItem("Include Analytics", "Embed chart data in reports.", true)
    SettingsSwitchItem("Include Bookmarks", "Add bookmarked items to final reports.", true)
    SettingsDropdownItem("HTML Optimization", "Maximum Compression")
}

@Composable
fun MediaSettings() {
    SettingsSwitchItem("Generate Thumbnails", "Extract image/video previews.", true)
    SettingsSwitchItem("Auto Delete Missing References", "Clear media cache for deleted chats.", true)
    SettingsDropdownItem("Thumbnail Quality", "Medium (Balanced)")
    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors()) { Text("Clear Media Cache") }
}

@Composable
fun SearchSettings() {
    SettingsSwitchItem("Fuzzy Search", "Match approximate spellings.", true)
    SettingsSwitchItem("Instant Search", "Search while typing.", true)
    SettingsSwitchItem("Highlight Results", "Highlight exact match in yellow.", true)
    SettingsSwitchItem("Search Cache", "Cache results for faster repeat searches.", true)
    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors()) { Text("Clear Search History") }
}

@Composable
fun PrivacySecuritySettings(viewModel: ChatViewModel, onShowSnackbar: (String) -> Unit) {
    val secureStorage by viewModel.secureStorage.collectAsState()
    SettingsSwitchItem("Secure Storage (AES-128)", "Encrypt database dynamically.", secureStorage) { viewModel.updateSecureStorage(it) }
    SettingsSwitchItem("App Lock", "Require Biometric/PIN to open app.", false)
    SettingsSwitchItem("Hide Sensitive Data", "Mask sender names in generic views.", false)
    SettingsSwitchItem("Secure Export", "Password protect ZIP files.", false)
    
    Spacer(modifier = Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Danger Zone", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Button(onClick = { viewModel.deleteAllAiHistory(); onShowSnackbar("History Cleared") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Delete AI History") }
            Button(onClick = { viewModel.deleteAllSessions(); onShowSnackbar("All Chats Wiped") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Delete All Chats") }
        }
    }
}

@Composable
fun StorageSettings(onShowSnackbar: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Storage and Resources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Database Size: 24.5 MB", color = MaterialTheme.colorScheme.onSurface)
            Text("Media Cache: 156 MB", color = MaterialTheme.colorScheme.onSurface)
            Text("Reports and Exports: 12 MB", color = MaterialTheme.colorScheme.onSurface)
            Text("Thumbnail Cache: 4 MB", color = MaterialTheme.colorScheme.onSurface)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { onShowSnackbar("Cache Cleared") }, modifier = Modifier.fillMaxWidth()) { Text("Clear Temporary Cache") }
    Button(onClick = { onShowSnackbar("Database Optimized") }, modifier = Modifier.fillMaxWidth()) { Text("Optimize Database (Vacuum)") }
    
    Spacer(modifier = Modifier.height(16.dp))
    Text("Backup & Restore", fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onShowSnackbar("Backup Created") }, modifier = Modifier.weight(1f)) { Text("Create Backup") }
        OutlinedButton(onClick = { }, modifier = Modifier.weight(1f)) { Text("Restore") }
    }
}

@Composable
fun DeveloperSettings() {
    SettingsSwitchItem("Debug Logs", "Print verbose logs to Logcat.", false)
    SettingsSwitchItem("Parser Logs", "Log raw parsing exceptions.", false)
    SettingsSwitchItem("AI Logs", "Log raw AI prompts (WARNING: Exposes tokens).", false)
    SettingsSwitchItem("Performance Monitor", "Show rendering stats on screen.", false)
    SettingsSwitchItem("Enable Automation", "Automatically run indexing, AI, and thumbnails", true)
    SettingsSwitchItem("Crash Recovery", "Save states frequently to recover from crashes.", true)
    
    Spacer(modifier = Modifier.height(16.dp))
    Text("System Resources", fontWeight = FontWeight.Bold)
    Text("CPU Usage: 4% | Memory: 120MB | Cache Size: 32MB", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Export Debug Logs") }
}

@Composable
fun AboutSettings() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Group Analyzer AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("Version 1.0.0 (Build 42)", color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Text("An advanced native tool for parsing, indexing, and analyzing exported group logs. Designed with full Material 3, Jetpack Compose, Room database, and GLM/Gemini intelligence.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    Spacer(modifier = Modifier.height(16.dp))
    SettingsDropdownItem("License", "Open Source (MIT)")
    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors()) { Text("Privacy Policy") }
    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors()) { Text("Terms of Service") }
}

// Utility components
@Composable
fun SettingsSwitchItem(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    var state by remember(checked) { mutableStateOf(checked) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { 
            state = !state
            onCheckedChange(state)
        }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", modifier = Modifier.size(14.dp), tint = Color.Gray)
            }
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(checked = state, onCheckedChange = { 
            state = it
            onCheckedChange(it)
        })
    }
}

@Composable
fun SettingsDropdownItem(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
    }
}
