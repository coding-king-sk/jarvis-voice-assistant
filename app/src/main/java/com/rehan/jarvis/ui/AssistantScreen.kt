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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.PhotoCamera
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
    onRequestPermissions: () -> Unit,
    onOpenCamera: () -> Unit = {}
) {
    val state by engine.state.collectAsState()
    val messages by engine.messages.collectAsState()
    val partial by engine.partialText.collectAsState()
    val micLevel by engine.micLevel.collectAsState()

    var input by remember { mutableStateOf("") }
    var wakeWordOn by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, InkMid, Ink)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            TopBar(
                wakeWordOn = wakeWordOn,
                onToggleWakeWord = {
                    if (wakeWordOn) {
                        onStopService()
                        wakeWordOn = false
                    } else {
                        if (hasMicPermission()) {
                            onStartService()
                            wakeWordOn = true
                        } else {
                            onRequestPermissions()
                        }
                    }
                },
                onReset = { engine.newConversation() }
            )

            IrisOrb(
                state = state,
                level = micLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )

            StatusText(state = state, partial = partial)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyState(onSuggestion = { engine.sendText(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                    }
                }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        engine.sendText(input.trim())
                        input = ""
                    }
                },
                listening = state == AssistantState.LISTENING,
                onMic = {
                    if (!hasMicPermission()) {
                        onRequestPermissions()
                    } else if (state == AssistantState.LISTENING) {
                        engine.stopListening()
                    } else {
                        engine.startListening()
                    }
                },
                onCamera = onOpenCamera
            )
        }
    }
}

@Composable
private fun TopBar(
    wakeWordOn: Boolean,
    onToggleWakeWord: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Glass)
                .border(1.dp, GlassBorder, CircleShape)
                .clickable { onToggleWakeWord() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (wakeWordOn) Color(0xFF32E0A8) else Color(0xFF4A5170))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (wakeWordOn) "Sun raha hoon" else "Wake word band",
                color = TextLo,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.weight(1f))

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
                imageVector = Icons.Default.Refresh,
                contentDescription = "Nayi baat shuru karo",
                tint = TextLo,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusText(state: AssistantState, partial: String) {
    val text = when {
        partial.isNotBlank() -> partial
        state == AssistantState.LISTENING -> "Sun raha hoon..."
        state == AssistantState.THINKING -> "Soch raha hoon..."
        state == AssistantState.ACTING -> "Kaam kar raha hoon..."
        state == AssistantState.SPEAKING -> "Bol raha hoon..."
        else -> ""
    }

    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            text = text,
            color = TextLo,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "Battery kitni hai?",
        "Torch on karo",
        "Notifications sunao",
        "Agla gaana lagao"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Kuch bhi poochho",
            color = TextHi,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(14.dp))

        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Glass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = suggestion, color = TextLo, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.fromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (fromUser) 18.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 18.dp
                    )
                )
                .then(
                    if (fromUser) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4F7CFF), Color(0xFF8A5CF6))
                            )
                        )
                    } else {
                        Modifier.background(Glass)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = if (fromUser) Color.White else TextHi,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit,
    onCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Camera se sawaal
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Glass)
                .border(1.dp, GlassBorder, CircleShape)
                .clickable { onCamera() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Photo ke baare me poochho",
                tint = TextLo,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Glass)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TextHi, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty()) {
                                Text("Type karo ya bolo...", color = TextLo, fontSize = 15.sp)
                            }
                            inner()
                        }
                        if (value.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
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

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (listening) {
                            listOf(Color(0xFFFF5F6D), Color(0xFFFF8A5B))
                        } else {
                            listOf(Color(0xFF4F7CFF), Color(0xFF8A5CF6))
                        }
                    )
                )
                .clickable { onMic() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (listening) Icons.Default.Close else Icons.Default.Mic,
                contentDescription = if (listening) "Rok do" else "Bolo",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
