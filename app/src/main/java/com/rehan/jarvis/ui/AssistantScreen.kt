package com.rehan.jarvis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehan.jarvis.core.AssistantEngine
import com.rehan.jarvis.core.AssistantState
import com.rehan.jarvis.core.ChatMessage

private val Ink = Color(0xFF05060B)
private val InkMid = Color(0xFF0B1024)
private val Glass = Color(0x14FFFFFF)
private val GlassBorder = Color(0x1FFFFFFF)
private val TextHi = Color(0xFFF2F4FF)
private val TextLo = Color(0xFF9AA3C7)
private val Accent = Color(0xFF4F7CFF)

@Composable
fun AssistantScreen(
    engine: AssistantEngine,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    hasMicPermission: () -> Boolean,
    onRequestPermissions: () -> Unit
) {
    val state by engine.state.collectAsState()
    val messages by engine.messages.collectAsState()
    val partial by engine.partialText.collectAsState()

    var input by remember { mutableStateOf("") }
    var serviceOn by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Ink, InkMid, Ink)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(
                serviceOn = serviceOn,
                onToggleService = {
                    if (serviceOn) {
                        onStopService(); serviceOn = false
                    } else if (hasMicPermission()) {
                        onStartService(); serviceOn = true
                    } else {
                        onRequestPermissions()
                    }
                },
                onReset = { engine.newConversation() }
            )

            // ---------- Orb ----------
            IrisOrb(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )

            Spacer(Modifier.height(4.dp))

            StatusText(state = state, partial = partial)

            Spacer(Modifier.height(12.dp))

            // ---------- Transcript ----------
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyState(onSuggestion = { engine.sendText(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { msg -> MessageBubble(msg) }
                    }
                }
            }

            InputBar(
                input = input,
                onInputChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        engine.sendText(input); input = ""
                    }
                },
                listening = state == AssistantState.LISTENING,
                onMic = {
                    if (!hasMicPermission()) onRequestPermissions()
                    else if (state == AssistantState.LISTENING) engine.stopListening()
                    else engine.startListening()
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    serviceOn: Boolean,
    onToggleService: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Jarvis",
            color = TextHi,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.weight(1f))

        // Wake word pill
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (serviceOn) Accent.copy(alpha = 0.18f) else Glass)
                .border(1.dp, if (serviceOn) Accent.copy(alpha = 0.5f) else GlassBorder, CircleShape)
                .clickable { onToggleService() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (serviceOn) Color(0xFF32E0A8) else Color(0xFF57608A))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (serviceOn) "Sun raha hoon" else "Wake word off",
                color = if (serviceOn) TextHi else TextLo,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Glass)
                .border(1.dp, GlassBorder, CircleShape)
                .clickable { onReset() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Nayi baat",
                tint = TextLo,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusText(state: AssistantState, partial: String) {
    val label = when (state) {
        AssistantState.IDLE -> ""
        AssistantState.LISTENING -> partial.ifBlank { "Sun raha hoon\u2026" }
        AssistantState.THINKING -> "Soch raha hoon\u2026"
        AssistantState.ACTING -> "Kaam kar raha hoon\u2026"
        AssistantState.SPEAKING -> "Bol raha hoon\u2026"
    }

    AnimatedVisibility(
        visible = label.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            color = TextHi.copy(alpha = 0.85f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val ideas = listOf(
        "Mummy ko call karo",
        "Subah 7 baje alarm laga do",
        "Volume 50 kar do",
        "YouTube kholo"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bolo, kya karna hai?",
            color = TextHi,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))
        ideas.forEach { idea ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable { onSuggestion(idea) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(text = idea, color = TextLo, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.horizontalGradient(
                            listOf(Color(0xFF4F7CFF), Color(0xFF8A5CF6))
                        )
                    } else {
                        SolidColor(Glass)
                    }
                )
                .padding(horizontal = 15.dp, vertical = 11.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) Color.White else TextHi.copy(alpha = 0.92f),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(Glass)
                .border(1.dp, GlassBorder, CircleShape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = TextHi, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (input.isEmpty()) {
                                Text("Type karo\u2026", color = TextLo, fontSize = 15.sp)
                            }
                            inner()
                        }
                        if (input.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Bhejo",
                                tint = Accent,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onSend() }
                            )
                        }
                    }
                }
            )
        }

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (listening) {
                        Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFF8A5B)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF4F7CFF), Color(0xFF8A5CF6)))
                    }
                )
                .clickable { onMic() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (listening) Icons.Default.Close else Icons.Default.Mic,
                contentDescription = if (listening) "Ruko" else "Bolo",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
