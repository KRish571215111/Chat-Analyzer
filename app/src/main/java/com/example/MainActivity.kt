package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChatViewModel
import com.example.ui.Screen
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.animation.togetherWith

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: ChatViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()
      val amoledDark by viewModel.amoledDark.collectAsState()
      val highContrast by viewModel.highContrast.collectAsState()
      val fontScale by viewModel.fontScale.collectAsState()
      val currentScreen by viewModel.currentScreen.collectAsState()

      MyApplicationTheme(
        themeMode = themeMode,
        amoledDark = amoledDark,
        highContrast = highContrast,
        fontScale = fontScale
      ) {
        // Handle Android System Back Button mapping
        BackHandler(enabled = currentScreen != Screen.Home && currentScreen != Screen.Onboarding) {
          viewModel.navigateBack()
        }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            if (currentScreen != Screen.Onboarding && currentScreen != Screen.Home && currentScreen != Screen.Workspaces && currentScreen != Screen.TaskCenter) {
              AppBottomNavigation(
                currentScreen = currentScreen,
                onNavigate = { viewModel.navigateTo(it) }
              )
            }
          }
        ) { innerPadding ->
          AppNavigation(
              currentScreen = currentScreen,
              viewModel = viewModel,
              modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
          )

          val pendingAiPrompt by viewModel.pendingAiPrompt.collectAsState()
          if (pendingAiPrompt != null) {
              var rememberChoice by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
              AlertDialog(
                  onDismissRequest = { viewModel.cancelAiConsent() },
                  title = { Text("AI Transparency & Privacy", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                  text = {
                      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                          Text("You are about to send data to an external AI service.")
                          Text("Provider: Gemini / Open AI (depending on settings)", style = MaterialTheme.typography.bodySmall)
                          Text("Model: ${viewModel.zaiModelSetting.collectAsState().value}", style = MaterialTheme.typography.bodySmall)
                          Text("Data to be sent:", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                          Text("- The prompt you entered or selected", style = MaterialTheme.typography.bodySmall)
                          Text("- The current chat context or image (depending on the action)", style = MaterialTheme.typography.bodySmall)
                          Spacer(modifier = Modifier.height(16.dp))
                          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberChoice = !rememberChoice }) {
                              Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                              Text("Remember my choice (Don't ask again)", style = MaterialTheme.typography.bodySmall)
                          }
                      }
                  },
                  confirmButton = {
                      Button(onClick = { viewModel.confirmAiConsent(rememberChoice) }) {
                          Text("Proceed")
                      }
                  },
                  dismissButton = {
                      TextButton(onClick = { viewModel.cancelAiConsent() }) {
                          Text("Cancel")
                      }
                  }
              )
          }
        }
      }
    }
  }
}

@Composable
fun AppBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val sessionId = when (currentScreen) {
        is Screen.ChatDashboard -> currentScreen.sessionId
        is Screen.MessageViewer -> currentScreen.sessionId
        is Screen.Participants -> currentScreen.sessionId
        is Screen.MediaGallery -> currentScreen.sessionId
        is Screen.Bookmarks -> currentScreen.sessionId
        is Screen.AiAssistant -> currentScreen.sessionId
        is Screen.Analytics -> currentScreen.sessionId
        is Screen.Reports -> currentScreen.sessionId
        else -> null
    } ?: return

    NavigationBar(
        modifier = Modifier.testTag("app_bottom_navigation")
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            NavigationBarItem(
                selected = currentScreen is Screen.ChatDashboard,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.ChatDashboard(sessionId)) 
                },
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                label = { Text("Dashboard") },
                modifier = Modifier.width(80.dp)
            )
            NavigationBarItem(
                selected = currentScreen is Screen.MessageViewer,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.MessageViewer(sessionId)) 
                },
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Messages") },
                label = { Text("Messages") },
                modifier = Modifier.width(80.dp)
            )
            NavigationBarItem(
                selected = currentScreen is Screen.Participants,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.Participants(sessionId)) 
                },
                icon = { Icon(Icons.Default.People, contentDescription = "Members") },
                label = { Text("Members") },
                modifier = Modifier.width(80.dp)
            )
            NavigationBarItem(
                selected = currentScreen is Screen.MediaGallery,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.MediaGallery(sessionId)) 
                },
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery") },
                label = { Text("Gallery") },
                modifier = Modifier.width(80.dp)
            )
            NavigationBarItem(
                selected = currentScreen is Screen.Analytics,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.Analytics(sessionId)) 
                },
                icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                label = { Text("Analytics") },
                modifier = Modifier.width(80.dp)
            )
            NavigationBarItem(
                selected = currentScreen is Screen.Reports,
                onClick = { 
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onNavigate(Screen.Reports(sessionId)) 
                },
                icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                label = { Text("Reports") },
                modifier = Modifier.width(80.dp)
            )
        }
    }
}

@Composable
fun AppNavigation(
    currentScreen: Screen,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)).togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)))
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            is Screen.Onboarding -> OnboardingScreen(viewModel, modifier = modifier)
            is Screen.Home -> HomeScreen(viewModel, modifier = modifier)
            is Screen.Workspaces -> WorkspacesScreen(
                viewModel = viewModel,
                onNavigateToTaskCenter = { viewModel.navigateTo(Screen.TaskCenter) },
                modifier = modifier
            )
            is Screen.TaskCenter -> TaskCenterScreen(viewModel, modifier = modifier)
            is Screen.ChatDashboard -> ChatDashboardScreen(viewModel, modifier = modifier)
            is Screen.ParticipantProfile -> ParticipantProfileScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                initialParticipantId = screen.participantId,
                modifier = modifier
            )
            is Screen.Participants -> ParticipantsScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                modifier = modifier
            )
            is Screen.AiAssistant -> AiAssistantScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                modifier = modifier
            )
            is Screen.MessageViewer -> MessageViewerScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                modifier = modifier
            )
            is Screen.Bookmarks -> BookmarksScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                modifier = modifier
            )
            is Screen.MediaGallery -> MediaGalleryScreen(
                viewModel = viewModel,
                sessionId = screen.sessionId,
                modifier = modifier
            )
            is Screen.Settings -> SettingsScreen(viewModel, modifier = modifier)
            is Screen.ImportWizard -> com.example.ui.screens.ImportWizardScreen(viewModel, modifier = modifier)
            is Screen.Analytics -> AnalyticsScreen(viewModel, screen.sessionId, modifier)
            is Screen.Reports -> ReportsScreen(viewModel, screen.sessionId, modifier)
            is Screen.MemberImport -> com.example.ui.screens.MemberImportScreen(viewModel, screen.sessionId, onBack = { viewModel.navigateBack() })
            is Screen.ParticipantComparison -> com.example.ui.screens.ParticipantComparisonScreen(viewModel, screen.sessionId, onBack = { viewModel.navigateBack() })
        }
    }
}
