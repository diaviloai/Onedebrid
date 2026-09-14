package com.onedebrid.app.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onNavigateToPlayer: (MediaType, String, String?, StreamCandidate?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.overview,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.onPlayClicked() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.details_play))
                }
            }
        }

        if (uiState.showStreamPicker) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.onDismissStreamPicker() }
            ) {
                StreamPickerContent(
                    streams = uiState.availableStreams,
                    onStreamSelected = { stream ->
                        viewModel.onStreamSelected(stream)
                        onNavigateToPlayer(
                            viewModel.mediaType,
                            viewModel.mediaId,
                            null,
                            stream
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamPickerContent(
    streams: List<StreamCandidate>,
    onStreamSelected: (StreamCandidate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.details_select_stream),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (streams.isEmpty()) {
            Text(
                text = stringResource(R.string.details_no_streams),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(streams) { stream ->
                    Button(
                        onClick = { onStreamSelected(stream) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = stream.title)
                    }
                }
            }
        }
    }
}
