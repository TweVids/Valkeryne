package com.twevids.valkeryne

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twevids.valkeryne.ui.ChatViewModel
import com.twevids.valkeryne.ui.components.CameraPreview
import com.twevids.valkeryne.ui.components.SettingsDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val context = LocalContext.current

            val settings by viewModel.settings.collectAsState()
            val messages by viewModel.messages.collectAsState()
            val isConnected by viewModel.isConnected.collectAsState()
            val isConnecting by viewModel.isConnecting.collectAsState()
            val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
            val isCameraEnabled by viewModel.isCameraEnabled.collectAsState()
            val isFrontCamera by viewModel.isFrontCamera.collectAsState()
            val isHoldingToSpeak by viewModel.isHoldingToSpeak.collectAsState()

            var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

            // Request runtime permissions for Camera and Mic on launch
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Handled */ }

            LaunchedEffect(Unit) {
                val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!hasCamera || !hasAudio) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                }
            }

            // Most recent message from AI for subtitle overlay
            val latestAiMessage = messages.lastOrNull { it.sender == com.twevids.valkeryne.model.MessageSender.AI }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111827))
            ) {
                // 1. Full-screen Camera View
                if (isCameraEnabled) {
                    CameraPreview(
                        isFrontCamera = isFrontCamera,
                        onPreviewViewReady = { previewViewRef = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Minimalist camera-off dark background
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera Disabled",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Off",
                                color = Color(0xFF9CA3AF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 2. Top Bar: Settings Button (Dark Gray Circle) & Live Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings Button: Circle Dark Gray
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xCC1F2937), CircleShape)
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Live Connection Indicator Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xCC1F2937), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color(0xFF10B981) else if (isConnecting) Color(0xFFF59E0B) else Color(0xFFEF4444),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Live" else if (isConnecting) "Connecting..." else "Offline",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Subtitle / AI Speech Overlay
                if (latestAiMessage != null && (latestAiMessage.text.isNotEmpty() || latestAiMessage.reasoning.isNotEmpty() || latestAiMessage.isStreaming || latestAiMessage.error != null)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 140.dp, start = 20.dp, end = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xEE1F2937),
                            shadowElevation = 4.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!latestAiMessage.error.isNullOrEmpty()) {
                                    Text(
                                        text = latestAiMessage.error,
                                        color = Color(0xFFF87171),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    // Reasoning thoughts
                                    if (latestAiMessage.reasoning.isNotEmpty()) {
                                        Text(
                                            text = "Thought: ${latestAiMessage.reasoning}",
                                            color = Color(0xFFA78BFA),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    // Transcribed response text
                                    if (latestAiMessage.text.isNotEmpty()) {
                                        Text(
                                            text = latestAiMessage.text,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    } else if (latestAiMessage.isStreaming) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color(0xFF60A5FA),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Listening & analyzing...",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Bottom Controls: Audio Recorder (Center Circle) & Camera Toggle Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status text for Hold to Speak
                    Text(
                        text = if (isHoldingToSpeak) "Listening... Release to send" else "Hold button to speak to Gemini Live",
                        color = if (isHoldingToSpeak) Color(0xFF60A5FA) else Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                        fontWeight = if (isHoldingToSpeak) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flip Camera Button
                        IconButton(
                            onClick = { viewModel.flipCamera() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xCC1F2937), CircleShape)
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Flip Camera",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Middle Button: Circle Audio Recorder (Hold to speech -> sends model current frame)
                        val buttonScale by animateFloatAsState(
                            targetValue = if (isHoldingToSpeak) 1.18f else 1.0f,
                            label = "buttonScale"
                        )
                        val buttonColor by animateColorAsState(
                            targetValue = if (isHoldingToSpeak) Color(0xFFDC2626) else Color(0xFF2563EB),
                            label = "buttonColor"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .scale(buttonScale)
                                .background(buttonColor, CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            // Capture current frame from camera preview view
                                            val currentFrame = previewViewRef?.bitmap
                                            viewModel.onHoldToSpeechStart(currentFrame)
                                            tryAwaitRelease()
                                            viewModel.onHoldToSpeechEnd()
                                        }
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = if (isHoldingToSpeak) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Hold to Speech",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Camera Toggle Button: Circle near micro button
                        IconButton(
                            onClick = { viewModel.toggleCamera() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (isCameraEnabled) Color(0xCC1F2937) else Color(0xCCDC2626),
                                    CircleShape
                                )
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Toggle Camera",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Settings Dialog Modal
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
