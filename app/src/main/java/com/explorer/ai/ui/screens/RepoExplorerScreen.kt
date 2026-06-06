package com.explorer.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorer.ai.data.GitTreeItem
import com.explorer.ai.ui.UIWorkspaceState

@Composable
fun RepoExplorerScreen(
    state: UIWorkspaceState,
    onQueryChange: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onFileSelected: (GitTreeItem) -> Unit,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onResetCredentials: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.repoSearchQuery,
                onValueChange = onQueryChange,
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
                onClick = onSearchTriggered,
                enabled = !state.isTreeLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("EXPLORE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onResetCredentials) {
                Text("⚙️", fontSize = 20.sp)
            }
        }

        // Workspace Panels (Split layout emulation)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
                    Text(
                        text = state.treeError,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(16.dp).align(Alignment.Center)
                    )
                } else if (state.openFilePath != null) {
                    // Active Selected Code Presentation Screen
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📄 ${state.openFilePath.substringAfterLast("/")}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Context Active",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(6.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (state.isFileLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                            } else {
                                Text(
                                    text = state.openFileContent ?: "",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    // Render File Hierarchy Explorer
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(state.fileTreeNodes) { node ->
                            val leadIcon = if (node.type == "tree") "📁" else "📄"
                            val depthSpacer = "  ".repeat(node.path.count { it == '/' })
                            
                            Text(
                                text = "$depthSpacer$leadIcon ${node.path.substringAfterLast("/")}",
                                color = if (node.type == "tree") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFileSelected(node) }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Lower Workspace half: Terminal Conversational Thread Component
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    reverseLayout = false
                ) {
                    items(state.chatHistory) { msg ->
                        val markerColor = when (msg.sender) {
                            "User" -> MaterialTheme.colorScheme.secondary
                            "AI" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = "[${msg.sender.uppercase()}]",
                                color = markerColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = msg.body,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    state.activeAiTypingMessage?.let { typing ->
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = "[AI STREAMING...]",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = typing,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Interactive Dock Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.activePromptInput,
                        onValueChange = onPromptChange,
                        placeholder = { Text("Query AI over selection...", color = Color.DarkGray, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onSendPrompt,
                        enabled = state.activePromptInput.isNotBlank() && !state.isAiStreaming,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("🚀", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
