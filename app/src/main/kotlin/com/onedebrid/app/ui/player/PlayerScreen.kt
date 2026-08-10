package com.onedebrid.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.onedebrid.app.R
import com.onedebrid.app.coordinator.PlaybackState as CoordinatorState
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackState as PlayerLifecycleState
import com.onedebrid.app.ui.navigation.PendingPlaybackHolder

/**
 * The Player screen.
 *
 * This is the file responsible for closing the Session 19 gap documented in
 * PlayerViewModel: PlayerViewModel.onCleared() cannot reliably run the
 * suspend call inside EndPlaybackSessionUseCase, because Android cancels
 * viewModelScope before onCleared() runs. The fix belongs here, not in the
 * ViewModel — see the DisposableEffect below.
 *
 * ExoPlayer ownership:
 * PlayerViewModel never touches ExoPlayer directly (UI layer boundary rule,
 * Technical_standards.md). This screen creates and owns the ExoPlayer
 * instance, feeds it the resolved StreamSource once CoordinatorState becomes
 * Ready, and reports every lifecycle change back to the ViewModel via
 * onPlayerStateChanged(newState, positionMs) — the ViewModel has no other
 * way to know what the player is actually doing or where it currently is.
 *
 * request/profileId are no longer accepted as plain parameters. They are
 * read from PendingPlaybackHolder on entry — see that file for why nav args
 * alone can't carry a full PlaybackRequest, and for the known process-death
 * limitation that comes with this approach. onMissingRequest is invoked if
 * the holder is empty (e.g. this screen was reached via restored back stack
 * after process death rather than a real navigate() call from a screen that
 * set the holder first) — the caller (the nav graph) decides where to send
 * the user in that case, since "go back to Home" is a navigation decision,
 * not something this screen should hardcode.
 */
@Composable
fun PlayerScreen(
    pendingPlaybackHolder: PendingPlaybackHolder,
    onMissingRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Read the pending request exactly once, at first composition, and
    // remember it for the lifetime of this screen instance. Reading it
    // inside `remember` rather than on every recomposition matters here:
    // consume() clears the holder as a side effect, so calling it more than
    // once per screen entry would find nothing the second time.
    val pending = remember { pendingPlaybackHolder.consume() }

    // ExoPlayer is created once per screen instance and lives for as long as
    // this composable is in the composition. `remember` (not
    // rememberSaveable — ExoPlayer isn't parcelable) ties its lifetime to
    // composition, matching the DisposableEffect below that releases it.
    // Created unconditionally even if `pending` is null, so the missing-
    // request DisposableEffect below has a consistent instance to release.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    // Handles the case where PendingPlaybackHolder had nothing to give —
    // see the class doc on PendingPlaybackHolder for when this happens.
    // Guarded so it fires once, not on every recomposition.
    if (pending == null) {
        DisposableEffect(Unit) {
            onMissingRequest()
            onDispose { exoPlayer.release() }
        }
        return
    }

    // Kick off resolution exactly once when the screen first composes.
    // PlaybackCoordinator.play() internally cancels any prior in-progress
    // request, so re-triggering this on recomposition would be wasted work,
    // not a correctness bug — but keying on Unit keeps it to a single call
    // per screen entry, matching how play() is documented to be used.
    DisposableEffect(Unit) {
        viewModel.play(pending.request, pending.profileId)
        onDispose {
            // This call is the actual fix for the gap flagged in Session 19.
            // onDispose fires reliably on back gesture, forward navigation,
            // or any other teardown of this screen — unlike onCleared(),
            // it runs while viewModelScope is still alive, so stop()'s
            // suspend call to EndPlaybackSessionUseCase actually completes
            // instead of being cancelled mid-flight.
            viewModel.stop()
            exoPlayer.release()
        }
    }

    // Feed the resolved stream to ExoPlayer as soon as the coordinator
    // reports Ready. Re-keying on the source id (not the whole state)
    // avoids reloading the same media if this recomposes for unrelated
    // reasons (e.g. playerLifecycleState changing).
    val