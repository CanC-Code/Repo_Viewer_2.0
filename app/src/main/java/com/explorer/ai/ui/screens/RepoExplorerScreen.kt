package com.explorer.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.explorer.ai.ui.ExplorerViewModel
import com.explorer.ai.ui.UIWorkspaceState
import com.explorer.ai.ui.ChatMessage

@Composable
fun RepoExplorerScreen(
    uiState: UIWorkspaceState,
    viewModel: ExplorerViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // --- TOP ACTION BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Native Engine Interface",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(onClick = { viewModel.toggleWorkspaceVisibility() }) {
                Text(if (uiState.isWorkspaceVisible) "Hide Workspace" else "Show Workspace")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- DYNAMIC WORKSPACE PANEL ---
        if (uiState.isWorkspaceVisible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = { Text("Target Repository URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.exploreGitHubRepository(uiState.searchQuery) }) {
                            Text("Initialize Connection")
                        }
                        OutlinedButton(onClick = { viewModel.purgeSavedCredentials() }) {
                            Text("Flush Auth", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    
                    // Render Active File Structure
                    if (uiState.files.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Active Buffer Pool:", fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                            items(uiState.files) { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${file.path} [${file.type}]", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { viewModel.loadSelectedFileContent(file.path) }) {
                                        Text("Mount")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- CONVERSATIONAL CONTEXT STREAM ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(uiState.chatHistory) { message ->
                    ChatMessageNode(message = message, viewModel = viewModel)
                }
                
                if (uiState.isLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SYSTEM STATUS INDICATOR ---
        uiState.systemStatusMessage?.let { status ->
            Text(
                text = "System: $status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // --- PROMPT INGESTION LAYER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.promptInput,
                onValueChange = { viewModel.updatePromptInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Execute logic query...") },
                singleLine = false,
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.dispatchChatPrompt(uiState.promptInput) },
                    enabled = uiState.promptInput.isNotBlank() && !uiState.isLoading
                ) {
                    Text("Execute")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { viewModel.retryLastPrompt() }) { Text("↺") }
                    FilledTonalButton(onClick = { viewModel.purgeChatHistory() }) { Text("⌫") }
                }
            }
        }
    }
}

@Composable
fun ChatMessageNode(message: ChatMessage, viewModel: ExplorerViewModel) {
    val isUser = message.sender == "User"
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else Color(0xFF2D2D2D)
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(containerColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            
            // Hardware Feedback Matrix (Only for AI responses)
            if (!isUser) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, 1) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == 1) Color.Green else contentColor.copy(alpha = 0.5f)
                        )
                    ) { Text("▲ Valid") }
                    
                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, -1) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == -1) Color.Red else contentColor.copy(alpha = 0.5f)
                        )
                    ) { Text("▼ Fault") }
                }
            }
        }
    }
}
