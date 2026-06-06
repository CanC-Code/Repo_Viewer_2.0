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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.explorer.ai.data.GitTreeItem
import com.explorer.ai.ui.ExplorerViewModel
import com.explorer.ai.ui.UIWorkspaceState

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper Command Bar
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.repoSearchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("owner/repository", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.exploreGitHubRepository() },
                enabled = !state.isTreeLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("EXPLORE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { viewModel.purgeSavedCredentials() }) { Text("⚙️", fontSize = 20.sp) }
        }

        // QOL Divider: Collapse/Expand Workspace Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { viewModel.toggleWorkspaceVisibility() }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (state.isWorkspaceExpanded) "▼ COLLAPSE FILE BROWSER ▼" else "▲ EXPAND FILE BROWSER ▲",
                color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.isWorkspaceExpanded) {
                // Top Workspace half: Tree Structure & Selected File Inspection Viewport
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (state.isTreeLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    } else if (state.treeError != null) {
                        Text(text = state.treeError, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp).align(Alignment.Center))
                    } else if (state.openFilePath != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.3f)).padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 ${state.openFilePath.substringAfterLast("/")}",
                                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                )
                                Row {
                                    Text("CLOSE", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { viewModel.exploreGitHubRepository() }.padding(horizontal = 8.dp, vertical = 4.dp))
                                    Text("COPY RAW", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(state.openFileContent ?: "")) }.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(6.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                                if (state.isFileLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                                else Text(text = state.openFileContent ?: "", color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            // Compute hierarchical visibility inline
                            val visibleNodes = state.fileTreeNodes.filter { node ->
                                val segments = node.path.split("/")
                                var isVisible = true
                                var currentPath = ""
                                for (i in 0 until segments.size - 1) {
                                    currentPath = if (currentPath.isEmpty()) segments[i] else "$currentPath/${segments[i]}"
                                    if (!state.expandedFolders.contains(currentPath)) { isVisible = false; break }
                                }
                                isVisible
                            }

                            items(visibleNodes) { node ->
                                val isExpanded = state.expandedFolders.contains(node.path)
                                val leadIcon = if (node.type == "tree") if (isExpanded) "📂" else "📁" else "📄"
                                val depthSpacer = "  ".repeat(node.path.count { it == '/' })
                                
                                Text(
                                    text = "$depthSpacer$leadIcon ${node.path.substringAfterLast("/")}",
                                    color = if (node.type == "tree") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.loadSelectedFileContent(node) }.padding(vertical = 4.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Lower Workspace half: Terminal Conversational Thread Component
            Column(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), reverseLayout = false) {
                    items(state.chatHistory) { msg ->
                        val isAI = msg.sender == "AI"
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "[${msg.sender.uppercase()}]",
                                    color = if (isAI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                )
                                // QOL Interaction Toolbar for AI messages
                                if (isAI) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (msg.feedbackState == 1) "👍" else "♡", fontSize = 12.sp, modifier = Modifier.clickable { viewModel.rateMessage(msg.id, 1) })
                                        Text(if (msg.feedbackState == -1) "👎" else "🤍", fontSize = 12.sp, modifier = Modifier.clickable { viewModel.rateMessage(msg.id, -1) })
                                        Text("📋", fontSize = 12.sp, modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(msg.body)) })
                                    }
                                }
                            }
                            MessageContentParser(msg.body, clipboardManager)
                        }
                    }
                    
                    state.activeAiTypingMessage?.let { typing ->
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("[AI STREAMING...]", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                MessageContentParser(typing, clipboardManager)
                            }
                        }
                    }
                }

                // Interactive Dock Input Row with QOL Attach & Retry Buttons
                Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { documentPickerLauncher.launch("*/*") }) { Text("📎", fontSize = 20.sp) }
                    
                    OutlinedTextField(
                        value = state.activePromptInput,
                        onValueChange = { viewModel.updatePromptInput(it) },
                        placeholder = { Text("Query AI over selection...", color = Color.DarkGray, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f), maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary, unfocusedBorderColor = Color.DarkGray),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    if (state.chatHistory.isNotEmpty() && !state.isAiStreaming) {
                        IconButton(onClick = { viewModel.retryLastPrompt() }) { Text("🔄", fontSize = 20.sp) }
                    }
                    
                    Button(
                        onClick = { viewModel.dispatchChatPrompt() },
                        enabled = state.activePromptInput.isNotBlank() && !state.isAiStreaming,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("🚀", fontSize = 16.sp) }
                }
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
                Text(text = part.trim(), color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                val lines = part.lines()
                val lang = lines.firstOrNull()?.trim() ?: ""
                val code = if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else part.trim()

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.DarkGray, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.uppercase(), color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "COPY", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { clipboardManager.setText(AnnotatedString(code)) }.padding(4.dp)
                        )
                    }
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                        Text(text = code, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
