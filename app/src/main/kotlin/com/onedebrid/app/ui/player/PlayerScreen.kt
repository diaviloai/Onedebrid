package com.onedebrid.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.onedebrid.app.R
import com.onedebrid.app.domain.model.AppError

@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onClearedPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.isResolving -> {
                ResolvingContent()
            }
            uiState.error != null -> {
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = viewModel::retryResolve,
                    onNavigateBack = onNavigateBack
                )
            }
            uiState.player != null -> {
                PlayerSurface(player = uiState.player!!)
            }
        }
    }
}

@Composable
private fun ResolvingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.player_resolving),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

@Composable
private fun ErrorContent(
    error: AppError,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.player_error_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.message ?: error.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (error.isRecoverable) {
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.player_retry))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedButton(onClick = onNavigateBack) {
            Text(text = stringResource(R.string.player_back))
        }
    }
}

@Composable
private fun PlayerSurface(player: Player) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
            }
        },
        update = { playerView ->
            playerView.player = player
        },
        modifier = Modifier.fillMaxSize()
    )
}
