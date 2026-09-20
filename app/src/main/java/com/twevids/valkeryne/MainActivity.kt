package com.twevids.valkeryne

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twevids.valkeryne.ui.ChatViewModel
import com.twevids.valkeryne.ui.components.ChatInputBar
import com.twevids.valkeryne.ui.components.ChatMessageBubble
import com.twevids.valkeryne.ui.components.Header
import com.twevids.valkeryne.ui.components.SettingsDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ChatViewModel = viewModel()

            MaterialTheme {
                val settings by viewModel.settings.collectAsState()
                val messages by viewModel.messages.collectAsState()
                val isConnected by viewModel.isConnected.collectAsState()
                val isConnecting by viewModel.isConnecting.collectAsState()
                val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
                val isPlayingAudio by viewModel.audioPlayer.isPlaying.collectAsState()
                val currentPlayingId by viewModel.audioPlayer.currentPlayingMessageId.collectAsState()

                val listState = rememberLazyListState()

                // Auto-scroll to bottom on new message
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFE5E7EB), // Gray background filling the app
                    topBar = {
                        Header(
                            modelId = settings.modelId,
                            isConnected = isConnected,
                            isConnecting = isConnecting,
                            onOpenSettings = { viewModel.openSettings() }
                        )
                    },
                    bottomBar = {
                        ChatInputBar(
                            onSendMessage = { text -> viewModel.sendMessage(text) }
                        )
                    }
                ) { innerPadding ->
                    // Large blank area on top displaying chat messages
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFFE5E7EB))
                    ) {
                        if (messages.isEmpty()) {
                            // Blank welcome state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Valkeryne Live AI",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1F2937),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Native Android real-time voice & reasoning chat with Gemini Live models.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF4B5563),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (settings.apiKey.isNotEmpty()) {
                                        "Connected to ${settings.modelId}. Type below to talk!"
                                    } else {
                                        "Tap the Settings icon at top-left to enter your Gemini API key."
                                    },
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .background(Color(0xFFD1D5DB), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    ChatMessageBubble(
                                        message = msg,
                                        isPlayingAudio = isPlayingAudio && currentPlayingId == msg.id,
                                        onTogglePlayAudio = { viewModel.toggleAudioPlayback(msg) }
                                    )
                                }
                            }
                        }
                    }

                    // Settings Dialog
                    if (isSettingsOpen) {
                        SettingsDialog(
                            settings = settings,
                            onDismiss = { viewModel.closeSettings() },
                            onSave = { updated -> viewModel.saveSettings(updated) }
                        )
                    }
                }
            }
        }
    }
}
