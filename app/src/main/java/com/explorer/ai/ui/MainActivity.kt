package com.explorer.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.explorer.ai.ui.screens.ApiKeySetupScreen
import com.explorer.ai.ui.screens.RepoExplorerScreen
import com.explorer.ai.ui.theme.ExplorerTheme

class MainActivity : ComponentActivity() {

    private val explorerViewModel: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ExplorerTheme {
                val workspaceState by explorerViewModel.uiState.collectAsState()

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        workspaceState.isCheckingKey -> {
                            // Render loading spinner while checking local storage preferences
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        workspaceState.apiKey.isNullOrBlank() -> {
                            // Onboarding state route: Key configuration requested
                            ApiKeySetupScreen(
                                onKeyConfirmed = { inputKey ->
                                    explorerViewModel.updateApiKey(inputKey)
                                }
                            )
                        }
                        else -> {
                            // Active operational state route: Workspace panel open
                            RepoExplorerScreen(
                                state = workspaceState,
                                onQueryChange = { explorerViewModel.updateSearchQuery(it) },
                                onSearchTriggered = { explorerViewModel.exploreGitHubRepository() },
                                onFileSelected = { explorerViewModel.loadSelectedFileContent(it) },
                                onPromptChange = { explorerViewModel.updatePromptInput(it) },
                                onSendPrompt = { explorerViewModel.dispatchChatPrompt() },
                                onResetCredentials = { explorerViewModel.purgeSavedCredentials() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // Intercept back actions to close active file view contexts cleanly back to tree navigation
        val currentState = explorerViewModel.uiState.value
        if (currentState.openFilePath != null && !currentState.isFileLoading) {
            explorerViewModel.exploreGitHubRepository() // Forces re-evaluation / clearing of active node
        } else {
            super.onBackPressed()
        }
    }
}
