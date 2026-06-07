package com.explorer.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorer.ai.data.AppMessage
import com.explorer.ai.data.GitTreeItem
import com.explorer.ai.ui.ExplorerViewModel
import com.explorer.ai.ui.UIWorkspaceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoExplorerScreen(
    state: UIWorkspaceState,
    viewModel: ExplorerViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.ingestLocalDocument(context, it) }
    }

    if (state.isSettingsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSettingsSheet() },
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.chatHistory) { message ->
                MessageBubble(message = message, clipboardManager = clipboardManager)
            }
            if (!state.activeAiTypingMessage.isNullOrBlank()) {
                item {
                    MessageBubble(
                        message = AppMessage(sender = "AI", body = state.activeAiTypingMessage!!),
                        clipboardManager = clipboardManager,
                        isStreaming = true
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.activePromptInput,
                onValueChange = { viewModel.onPromptInputChanged(it) },
                modifier = Modifier.weight(1f),
                label = { Text("Enter your query", color = Color.LightGray) },
                singleLine = false,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color.DarkGray,
                    textColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Gray,
                    unfocusedBorderColor = Color.Gray
                )
            )
            Button(
                onClick = { viewModel.sendPrompt(state.activePromptInput) },
                enabled = !state.isAiStreaming
            ) {
                Text("Send", color = Color.White)
            }
        }
    }
}

@Composable
fun MessageBubble(message: AppMessage, clipboardManager: ClipboardManager, isStreaming: Boolean = false) {
    val bubbleColor = if (message.sender == "User") Color(0xFF007BFF) else Color(0xFF2D2D2D)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.sender == "User") Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                MessageContentParser(text = message.body, clipboardManager = clipboardManager)
            }
        }
    }
}

@Composable
fun MessageContentParser(text: String, clipboardManager: ClipboardManager) {
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                Text(
                    text = part.trim(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                val lines = part.lines()
                val lang = lines.firstOrNull()?.trim() ?: ""
                val code = if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else part.trim()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            lang.uppercase(),
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "COPY",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { clipboardManager.setText(AnnotatedString(code)) }
                                .padding(4.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = code,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}