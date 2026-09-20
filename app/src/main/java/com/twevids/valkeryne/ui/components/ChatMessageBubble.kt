package com.twevids.valkeryne.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twevids.valkeryne.model.ChatMessage
import com.twevids.valkeryne.model.MessageSender

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isPlayingAudio: Boolean,
    onTogglePlayAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReasoningExpanded by remember { mutableStateOf(true) }

    val isUser = message.sender == MessageSender.USER

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User Message Bubble (Right-aligned, rounded)
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                color = Color(0xFF1F2937),
                shadowElevation = 1.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.imageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = androidx.compose.ui.graphics.asImageBitmap(message.imageBitmap),
                            contentDescription = "Captured Image",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .androidx.compose.ui.draw.clip(RoundedCornerShape(10.dp))
                                .padding(bottom = if (message.text.isNotEmpty()) 8.dp else 0.dp)
                        )
                    }
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            color = Color(0xFFF9FAFB),
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        } else {
            // AI Message Bubble (Left-aligned, rounded)
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Inline Error State
                    if (!message.error.isNullOrEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.error,
                                color = Color(0xFFDC2626),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Reasoning / Thought Section
                    if (message.reasoning.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F3FF), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isReasoningExpanded = !isReasoningExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "Reasoning",
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reasoning Process",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6D28D9)
                                    )
                                }
                                Icon(
                                    imageVector = if (isReasoningExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = isReasoningExpanded) {
                                Text(
                                    text = message.reasoning,
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFF4B5563),
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // AI Answer Text
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            color = Color(0xFF111827),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    } else if (message.isStreaming && message.audioChunks.isEmpty() && message.error == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating response...", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }

                    // Audio Button directly below the response (or standalone if model returned only audio)
                    if (message.audioChunks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onTogglePlayAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAudio) Color(0xFF2563EB) else Color(0xFFF3F4F6)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPlayingAudio) Color(0xFF2563EB) else Color(0xFFD1D5DB)
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Audio",
                                tint = if (isPlayingAudio) Color.White else Color(0xFF1F2937),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingAudio) "Playing Audio..." else "Play Spoken Response (${message.audioChunks.size} chunks)",
                                color = if (isPlayingAudio) Color.White else Color(0xFF1F2937),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
