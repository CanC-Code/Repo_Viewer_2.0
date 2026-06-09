package com.explorer.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.explorer.ai.ui.screens.RepoExplorerScreen
import com.explorer.ai.ui.theme.ExplorerTheme

class MainActivity : ComponentActivity() {

    private val explorerViewModel: ExplorerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ExplorerTheme {
                val workspaceState by explorerViewModel.uiState.collectAsState()

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // No API key gate — go directly to the explorer screen
                    RepoExplorerScreen(
                        state = workspaceState,
                        viewModel = explorerViewModel
                    )
                }
            }
        }
    }
}
