package com.rehan.jarvis.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehan.jarvis.core.AssistantEngine
import com.rehan.jarvis.core.AssistantState
import com.rehan.jarvis.core.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Jarvis", fontWeight = FontWeight.Bold) })

        // ---- Wake word service toggle ----
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (serviceOn) "Wake word active" else "Wake word band hai",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (serviceOn) "Background me sun raha hoon" else "Start karo taaki bina app khole bol sako",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (serviceOn) {
                    OutlinedButton(onClick = { onStopService(); serviceOn = false }) { Text("Stop") }
                } else {
                    Button(onClick = {
                        if (hasMicPermission()) { onStartService(); serviceOn = true }
                        else onRequestPermissions()
                    }) { Text("Start") }
                }
            }
        }

        // ---- Chat ----
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Namaste!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Mic dabao aur bolo:\n\n\"Mummy ko call karo\"\n\"Subah 7 baje ka alarm laga do\"\n\"Volume 50 kar do\"\n\"YouTube kholo\"",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg -> MessageBubble(msg) }
                }
            }
        }

        // ---- Status line ----
        val status = when (state) {
            AssistantState.LISTENING -> partial.ifBlank { "Sun raha hoon…" }
            AssistantState.THINKING -> "Soch raha hoon…"
            AssistantState.ACTING -> "Kaam kar raha hoon…"
            AssistantState.SPEAKING -> "Bol raha hoon…"
            AssistantState.IDLE -> ""
        }
        if (status.isNotBlank()) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }

        // ---- Input row ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type karo…") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = { engine.sendText(input); input = "" }) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            )

            Spacer(Modifier.width(12.dp))

            val listening = state == AssistantState.LISTENING
            val scale by animateFloatAsState(if (listening) 1.15f else 1f, label = "mic")

            FloatingActionButton(
                onClick = {
                    if (!hasMicPermission()) onRequestPermissions()
                    else if (listening) engine.stopListening()
                    else engine.startListening()
                },
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale),
                shape = CircleShape,
                containerColor = if (listening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Bolo", tint = Color.White)
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
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
        }
    }
}
