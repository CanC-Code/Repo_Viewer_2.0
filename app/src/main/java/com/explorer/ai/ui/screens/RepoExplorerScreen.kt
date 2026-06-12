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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.explorer.ai.ui.ChatMessage
import com.explorer.ai.ui.ExplorerViewModel
import com.explorer.ai.ui.UIWorkspaceState
import com.explorer.ai.ui.ProgrammaticDiagram

@Composable
fun RepoExplorerScreen(
    uiState: UIWorkspaceState,
    viewModel: ExplorerViewModel
) {
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.ingestLocalDocument(it) } }
    )

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
        // ── Title bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Native Engine Interface",
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
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // ── Chat area ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
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
                contentPadding = PaddingValues(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.chatHistory, key = { it.id }) { message ->
                    ChatMessageBubble(message = message, viewModel = viewModel)
                }
                if (uiState.isLoading && uiState.loadingProgressText == null) {
                    item { ReasoningIndicator() }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Status bar ────────────────────────────────────────────────────────
        uiState.systemStatusMessage?.let {
            Text(
                "System: $it",
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

// ─── Message bubble ───────────────────────────────────────────────────────────

@Composable
fun ChatMessageBubble(message: ChatMessage, viewModel: ExplorerViewModel) {
    val isUser = message.sender == "User"
    val isSystem = message.sender == "System"

    val containerColor = when {
        isUser   -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> Color(0xFF1E1E1E)
        else     -> Color(0xFF232323)
    }
    val contentColor = when {
        isUser   -> MaterialTheme.colorScheme.onPrimaryContainer
        isSystem -> Color(0xFF9E9E9E)
        else     -> Color(0xFFE8E8E8)
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.80f else 0.95f)
                .clip(RoundedCornerShape(
                    topStart = if (isUser) 12.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                ))
                .background(containerColor)
                .padding(12.dp)
        ) {
            if (!isUser) {
                Text(
                    message.sender,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Render message body — detect and style code fences
            SelectionContainer {
                FormattedMessageBody(text = message.body, defaultColor = contentColor)
            }
            
            // Render Programmatic Diagram if intercepted from LLM output
            message.diagramTrigger?.let { triggerType ->
                Spacer(modifier = Modifier.height(12.dp))
                ProgrammaticDiagram(type = triggerType)
            }

            // Feedback row (AI only)
            if (!isUser && !isSystem) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, 1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == 1) Color(0xFF4CAF50)
                            else contentColor.copy(alpha = 0.3f)
                        )
                    ) { Text("▲ Valid", fontSize = 11.sp) }

                    TextButton(
                        onClick = { viewModel.rateMessage(message.id, -1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (message.feedbackState == -1) Color(0xFFCF6679)
                            else contentColor.copy(alpha = 0.3f)
                        )
                    ) { Text("▼ Fault", fontSize = 11.sp) }
                }
            }
        }
    }
}

/**
 * Renders message body with inline code fence support.
 * Lines inside ```...
``` fences are rendered in a monospace dark box.
 * Bold markers (**text**) are rendered as bold.
 */
@Composable
fun FormattedMessageBody(text: String, defaultColor: Color) {
    val segments = parseMessageSegments(text)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (segment in segments) {
            when (segment) {
                is MessageSegment.PlainText -> {
                    Text(
                        text = renderInlineMarkdown(segment.content),
                        color = defaultColor,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
                is MessageSegment.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = segment.content,
                            color = Color(0xFF80CBC4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

sealed class MessageSegment {
    data class PlainText(val content: String) : MessageSegment()
    data class CodeBlock(val content: String, val language: String = "") : MessageSegment()
}

fun parseMessageSegments(text: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val lines = text.lines()
    var inCode = false
    var codeLang = ""
    val codeBuffer = StringBuilder()
    val textBuffer = StringBuilder()

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (!inCode) {
                if (textBuffer.isNotEmpty()) {
                    segments.add(MessageSegment.PlainText(textBuffer.toString().trim()))
                    textBuffer.clear()
                }
                inCode = true
                codeLang = line.trim().removePrefix("
```").trim()
            } else {
                segments.add(MessageSegment.CodeBlock(codeBuffer.toString().trim(), codeLang))
                codeBuffer.clear()
                inCode = false
                codeLang = ""
            }
        } else {
            if (inCode) codeBuffer.appendLine(line)
            else textBuffer.appendLine(line)
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        segments.add(MessageSegment.CodeBlock(codeBuffer.toString().trim(), codeLang))
    }
    if (textBuffer.isNotEmpty()) {
        segments.add(MessageSegment.PlainText(textBuffer.toString().trim()))
    }

    return segments.filter {
        when (it) {
            is MessageSegment.PlainText -> it.content.isNotBlank()
            is MessageSegment.CodeBlock -> it.content.isNotBlank()
        }
    }
}

@Composable
fun renderInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        var cursor = 0
        for (match in boldRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
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
        Text("Reasoning...", style = MaterialTheme.typography.bodySmall, color = Color(0xFF777777))
    }
}
