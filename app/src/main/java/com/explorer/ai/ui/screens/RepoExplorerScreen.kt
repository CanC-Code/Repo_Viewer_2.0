package com.explorer.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorer.ai.ui.ChatMessage
import com.explorer.ai.ui.ExplorerViewModel
import com.explorer.ai.ui.UIWorkspaceState

@Composable
fun RepoExplorerScreen(
    uiState: UIWorkspaceState,
    viewModel: ExplorerViewModel
) {
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.ingestLocalDocument(it) } }
    )

    // Auto-scroll to newest message
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.chatHistory.size) {
        if (uiState.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Native Engine Interface",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { viewModel.toggleWorkspaceVisibility() }) {
                Text(if (uiState.isWorkspaceVisible) "Hide Workspace" else "Show Workspace")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Workspace panel ───────────────────────────────────────────────────
        if (uiState.isWorkspaceVisible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {

                    // Remote block
                    Text("Remote Environment",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = { Text("Target Repository URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.exploreGitHubRepository(uiState.searchQuery) }) {
                            Text("Initialize Connection")
                        }
                        OutlinedButton(onClick = { viewModel.purgeSavedCredentials() }) {
                            Text("Flush Auth", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Local ingestion block
                    Text("Local Architecture Mapping",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            documentLauncher.launch(
                                arrayOf("application/pdf", "text/plain", "application/epub+zip", "text/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(
                            if (uiState.isLoading && uiState.loadingProgressText != null)
                                uiState.loadingProgressText!!
                            else "Ingest Technical Manual (PDF)"
                        )
                    }

                    // Live progress bar during OCR
                    if (uiState.isLoading) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        uiState.loadingProgressText?.let {
                            Text(it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (uiState.files.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Active Buffer Pool:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                            items(uiState.files) { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${file.path} [${file.type}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.loadSelectedFileContent(file.path) }) {
                                        Text("Mount", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Chat area ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (uiState.chatHistory.isEmpty() && !uiState.isLoading) {
                Text(
                    "Ingest a document above, then ask anything about its contents.",
                    color = Color(0xFF555555),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.chatHistory, key = { it.id }) { message ->
                    ChatMessageNode(message = message, viewModel = viewModel)
                }

                // Reasoning spinner (only when NOT doing OCR — OCR progress is in workspace)
                if (uiState.isLoading && uiState.loadingProgressText == null) {
                    item { ReasoningIndicator() }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Status bar ────────────────────────────────────────────────────────
        uiState.systemStatusMessage?.let { status ->
            Text(
                text = "System: $status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── Input row ─────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = uiState.promptInput,
                onValueChange = { viewModel.updatePromptInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Execute logic query...") },
                singleLine = false,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { viewModel.dispatchChatPrompt(uiState.promptInput) },
                    enabled = uiState.promptInput.isNotBlank() && !uiState.isLoading
                ) { Text("Execute") }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.retryLastPrompt() },
                        enabled = !uiState.isLoading
                    ) { Text("↺") }
                    FilledTonalButton(
                        onClick = { viewModel.purgeChatHistory() },
                        enabled = !uiState.isLoading
                    ) { Text("⌫") }
                }
            }
        }
    }
}

// ─── Chat bubble ─────────────────────────────────────────────────────────────

@Composable
fun ChatMessageNode(message: ChatMessage, viewModel: ExplorerViewModel) {
    val isUser = message.sender == "User"
    val isSystem = message.sender == "System"

    val containerColor = when {
        isUser   -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else     -> Color(0xFF2A2A2A)
    }
    val contentColor = when {
        isUser   -> MaterialTheme.colorScheme.onPrimaryContainer
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        else     -> Color(0xFFEAEAEA)
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.80f else 0.94f)
                .background(containerColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (!isUser) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                lineHeight = 21.sp
            )

            // Feedback row (AI only)
            if (!isUser && !isSystem) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, 1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == 1) Color(0xFF4CAF50)
                            else contentColor.copy(alpha = 0.35f)
                        )
                    ) { Text("▲ Valid", fontSize = 11.sp) }

                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, -1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == -1) Color(0xFFCF6679)
                            else contentColor.copy(alpha = 0.35f)
                        )
                    ) { Text("▼ Fault", fontSize = 11.sp) }
                }
            }
        }
    }
}

// ─── Reasoning spinner ────────────────────────────────────────────────────────

@Composable
fun ReasoningIndicator() {
    val transition = rememberInfiniteTransition(label = "reason")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin"
    )
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("◌", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(angle))
        Text("Reasoning...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
    }
}
