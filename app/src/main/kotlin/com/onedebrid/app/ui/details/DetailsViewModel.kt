package com.onedebrid.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.ui.navigation.PlayerNavArgs
import com.onedebrid.app.usecase.GetEpisodesUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Details / Episode-picker screen.
 *
 * Modeled as a single data class rather than a sealed Loading/Success/Error
 * state, same reasoning as HomeUiState: there is exactly one thing this
 * screen is fundamentally waiting on ([media]), and [episodesError] /
 * [episodes] track a second, independent load that only applies to
 * MediaType.TV_SHOW. Keeping them as separate fields (rather than nesting a
 * sub-state) avoids forcing movie details through an episode-related state
 * machine that will never apply to them.
 *
 * [isLoadingMedia] covers the initial GetMediaByIdUseCase call. [mediaError]
 * is set if that call fails — expected to be AppError.AllProvidersUnavailable
 * in practice today since MetadataProvider is still StubMetadataProvider,
 * same caveat as HomeViewModel/GetMediaByIdUseCase's own doc comments.
 *
 * [episodes] / [isLoadingEpisodes] / [episodesError] only apply once [media]
 * has loaded successfully and its type is TV_SHOW — see loadMedia().
 */
data class DetailsUiState(
    val media: Media? = null,
    val isLoadingMedia: Boolean = true,
    val mediaError: AppError? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodesError: AppError? = null
)

/**
 * ViewModel for the Details / Episode-picker screen (Session 26).
 *
 * Reached only from Search today (SearchScreen navigates here for both
 * MediaType.MOVIE and MediaType.TV_SHOW results, closing the "TV_SHOW not
 * yet supported" gap flagged in SearchScreen's Session 25 doc comment).
 * Continue Watching's tap-to-resume flow (HomeViewModel.onItemClick) is
 * deliberately NOT routed through this screen — it navigates straight to
 * Player to preserve exact resumePositionMs resume behavior, which this
 * screen's play actions do not carry (see onPlayMovie/onPlayEpisode below).
 * This was an explicit scope decision, not an oversight — see
 * currentsprint.md Session 26 notes.
 *
 * Takes only a mediaId via SavedStateHandle and re-fetches the full Media
 * itself via GetMediaByIdUseCase on init, exactly like HomeViewModel used
 * to do for Continue Watching rows before Session 27 (see HomeViewModel's
 * own doc comment for why that screen no longer does this) — even though
 * SearchScreen already had a full Media in hand at the moment of the tap
 * that led here. This is a deliberate small inefficiency (one extra
 * cache-backed lookup) in exchange for a single, simple entry path into
 * this screen rather than two (one for callers with a Media already in
 * hand, one for callers with only an id) — see currentsprint.md Session 26
 * notes for the reasoning. This screen's own case is not quite the same
 * shape as Home's was: Details still needs the full Media for its own
 * rendering (title, overview, episode list), not just to pass an id
 * onward, so re-fetching here remains necessary regardless of what Player
 * does with its own copy.
 *
 * Session 27 change: onPlayMovie/onPlayEpisode no longer build a
 * PlaybackRequest or read the active profile — they emit nav args
 * (mediaId, optional episodeId) via [navigateToPlayer], and PlayerViewModel
 * resolves Media/Episode/active-profile itself once Player is reached (see
 * PlayerViewModel.kt). This ViewModel therefore no longer needs
 * GetActiveProfileUseCase or PendingPlaybackHolder (the latter deleted
 * this session) at all.
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase
) : ViewModel() {

    private val mediaId: String = checkNotNull(savedStateHandle["mediaId"]) {
        "DetailsScreen requires a mediaId nav argument"
    }

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    //// Session 27: carries PlayerNavArgs (ui.navigation — shared with
    // HomeViewModel) instead of Unit, since Route.Player.build() now needs
    // mediaId/episodeId as real nav args rather than reading a
    // PlaybackRequest out of PendingPlaybackHolder. A Channel rather than a
    // second StateFlow so the event fires exactly once and is never
    // replayed on recomposition/configuration change.
    private val _navigateToPlayer = Channel<PlayerNavArgs>(Channel.BUFFERED)
    val navigateToPlayer: Flow<PlayerNavArgs> = _navigateToPlayer.receiveAsFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            when (val result = getMediaByIdUseCase(mediaId)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        media = result.data,
                        isLoadingMedia = false
                    )
                    if (result.data.type == MediaType.TV_SHOW) {
                        loadEpisodes()
                    }
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMedia = false,
                        mediaError = result.error
                    )
                }
            }
        }
    }

    private fun loadEpisodes() {
        _uiState.value = _uiState.value.copy(isLoadingEpisodes = true, episodesError = null)
        viewModelScope.launch {
            when (val result = getEpisodesUseCase(mediaId)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        episodes = result.data,
                        isLoadingEpisodes = false
                    )
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingEpisodes = false,
                        episodesError = result.error
                    )
                }
            }
        }
    }

    /** Retry after a failed media load — re-runs the same load from scratch. */
    fun retryMedia() {
        _uiState.value = _uiState.value.copy(isLoadingMedia = true, mediaError = null)
        loadMedia()
    }

    /** Retry after a failed episode load. */
    fun retryEpisodes() {
        loadEpisodes()
    }

    /**
     * Play a MediaType.MOVIE. No-op if media hasn't loaded yet.
     *
     * Always starts from the beginning (resumeMs = null) — this screen has
     * no resume-position context, unlike Continue Watching's flow. A future
     * enhancement could check Continue Watching for this mediaId and offer
     * resume-from-here, but that's out of scope for this session (see
     * currentsprint.md Session 26 notes — unchanged reasoning as of
     * Session 27).
     */
    fun onPlayMovie() {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = null,
                resumeMs = null
            )
        )
    }

    /**
     * Play a specific episode of a MediaType.TV_SHOW. Same no-op guard and
     * same "always starts from the beginning" caveat as onPlayMovie().
     */
    fun onPlayEpisode(episode: Episode) {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = episode.id,
                resumeMs = null
            )
        )
    }