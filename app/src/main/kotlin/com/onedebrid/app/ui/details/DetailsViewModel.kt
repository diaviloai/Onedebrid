package com.onedebrid.app.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.Episode
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.MediaType
import com.onedebrid.app.domain.model.StreamCandidate
import com.onedebrid.app.ui.navigation.PlayerNavArgs
import com.onedebrid.app.usecase.GetEpisodesUseCase
import com.onedebrid.app.usecase.GetMediaByIdUseCase
import com.onedebrid.app.usecase.GetStreamCandidatesUseCase
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
 *
 * [picker] (stream-candidate picker feature) is tracked as its own
 * PickerUiState below, deliberately NOT nested inside this data class's
 * other fields — it is a fully independent lifecycle (closed/loading/
 * loaded/error) that can open and close repeatedly over this screen's
 * lifetime, unlike [media]/[episodes] which each load once. Kept as a
 * sibling field for the same "don't force an unrelated concern through a
 * shared state machine" reasoning already used for episodes vs. media
 * above.
 */
data class DetailsUiState(
    val media: Media? = null,
    val isLoadingMedia: Boolean = true,
    val mediaError: AppError? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodesError: AppError? = null,
    val picker: PickerUiState = PickerUiState.Closed
)

/**
 * State for the manual stream-candidate picker sheet (added after Session
 * 30, the feature flagged as Next Step #1 in that session's handoff).
 *
 * Closed: the default. The picker sheet is not shown.
 * Loading: onChooseStream() was called; GetStreamCandidatesUseCase is in
 * flight. [episode] is carried through from the call that triggered this,
 * so Loaded/Error (and eventually onCandidateSelected()) know which target
 * — the movie itself, or which specific TV episode — the candidates being
 * fetched/shown apply to, without a separate stored field on the
 * ViewModel. null [episode] means the picker was opened for a
 * MediaType.MOVIE (mirrors onPlayMovie/onPlayEpisode's own null-episode-id
 * convention below).
 * Loaded: candidates fetched successfully. May be an empty list — an
 * empty list and Error are deliberately distinct states (mirrors
 * RepositoryResult's own success/failure split): an empty list means the
 * search legitimately found nothing, an Error means the search itself
 * failed. DetailsScreen renders each differently (see that file).
 * Error: GetStreamCandidatesUseCase failed. Retry re-runs onChooseStream()
 * for the same [episode].
 */
sealed interface PickerUiState {
    data object Closed : PickerUiState
    data class Loading(val episode: Episode?) : PickerUiState
    data class Loaded(val episode: Episode?, val candidates: List<StreamCandidate>) : PickerUiState
    data class Error(val episode: Episode?, val error: AppError) : PickerUiState
}

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
 *
 * Stream-candidate picker feature (added after Session 30): Play remains
 * the primary, one-tap action (onPlayMovie/onPlayEpisode, unchanged,
 * still emit preferredSource = null so PlayerViewModel resolves Smart
 * Defaults) — per the discussed and confirmed design, manual picking is a
 * deliberate override, not a replacement, preserving Project_Design.md's
 * "Zero-Click to Content" / Smart Defaults principles. onChooseStream()
 * opens a separate sheet (see [picker] on DetailsUiState /
 * DetailsScreen.kt) fetching the same StreamCandidate list
 * ResolvePlaybackUseCase.resolveSmartDefault() already draws from
 * (GetStreamCandidatesUseCase, a thin wrapper over
 * MediaRepository.searchStreamsByMedia() — see that Use Case's own doc
 * comment for why it exists as a separate small file). Selecting a
 * candidate (onCandidateSelected()) emits the same [navigateToPlayer]
 * event as Play, just with preferredSource set — PlayerViewModel then
 * hands that exact candidate to PlaybackCoordinator.play() instead of
 * resolving one itself (see PlayerViewModel.resolveAndPlay()).
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getEpisodesUseCase: GetEpisodesUseCase,
    private val getStreamCandidatesUseCase: GetStreamCandidatesUseCase
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
     *
     * preferredSource = null (explicit, not just the default arg) — Play
     * always means Smart Default, per the stream-candidate picker
     * feature's confirmed design. A manual pick only ever happens via
     * onCandidateSelected() below.
     */
    fun onPlayMovie() {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = null,
                resumeMs = null,
                preferredSource = null
            )
        )
    }

    /**
     * Play a specific episode of a MediaType.TV_SHOW. Same no-op guard,
     * same "always starts from the beginning" caveat, and same explicit
     * preferredSource = null as onPlayMovie().
     */
    fun onPlayEpisode(episode: Episode) {
        val media = _uiState.value.media ?: return
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = episode.id,
                resumeMs = null,
                preferredSource = null
            )
        )
    }

    /**
     * Opens the stream-candidate picker sheet and fetches candidates.
     *
     * [episode] null means "picking for the movie itself" (or, for a TV
     * show, this method is not expected to be called without an episode —
     * DetailsScreen only exposes a "choose a stream" affordance once a
     * specific episode row is in context, same as onPlayEpisode requiring
     * one; there is no top-level "choose a stream for this show" action
     * since a show has no single stream). Carried through into every
     * PickerUiState variant so onCandidateSelected() knows which
     * episodeId (if any) to attach to the resulting nav args without a
     * separate stored field.
     *
     * No no-op guard on media being loaded: this is only ever reachable
     * from a Play-adjacent affordance that itself only renders once media
     * has loaded (mirrors onPlayMovie/onPlayEpisode's own assumption,
     * enforced by DetailsScreen's layout rather than re-checked here).
     */
    fun onChooseStream(episode: Episode? = null) {
        val media = _uiState.value.media ?: return
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Loading(episode))
        viewModelScope.launch {
            when (val result = getStreamCandidatesUseCase(media, episode)) {
                is RepositoryResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        picker = PickerUiState.Loaded(episode, result.data)
                    )
                }

                is RepositoryResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        picker = PickerUiState.Error(episode, result.error)
                    )
                }
            }
        }
    }

    /** Retry after a failed candidate fetch — re-runs it for the same episode. */
    fun retryChooseStream() {
        val currentPicker = _uiState.value.picker
        val episode = when (currentPicker) {
            is PickerUiState.Error -> currentPicker.episode
            is PickerUiState.Loading -> currentPicker.episode
            is PickerUiState.Loaded -> currentPicker.episode
            PickerUiState.Closed -> null
        }
        onChooseStream(episode)
    }

    /**
     * Closes the picker sheet without selecting anything — e.g. the user
     * dismisses it (scrim tap, back gesture, explicit close button).
     */
    fun onDismissPicker() {
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Closed)
    }

    /**
     * User manually selected [candidate] from the picker sheet. Emits the
     * same [navigateToPlayer] event Play uses, but with preferredSource
     * set — PlayerViewModel then hands this exact candidate to
     * PlaybackCoordinator.play() instead of resolving one itself (see
     * PlayerViewModel.resolveAndPlay()). The episodeId is read from
     * PickerUiState.Loaded (the only state this method is expected to be
     * called from — DetailsScreen only shows selectable candidates in that
     * state) rather than re-passed as a parameter, so the caller can't
     * accidentally mismatch which episode a candidate was actually fetched
     * for. No-ops if picker isn't in Loaded state, or if media hasn't
     * loaded (mirrors onPlayMovie/onPlayEpisode's guard).
     *
     * Closes the picker sheet as part of the same state update that emits
     * navigation, so there's no visible frame where the sheet is still
     * open while navigation is already underway.
     */
    fun onCandidateSelected(candidate: StreamCandidate) {
        val media = _uiState.value.media ?: return
        val loadedPicker = _uiState.value.picker as? PickerUiState.Loaded ?: return
        _uiState.value = _uiState.value.copy(picker = PickerUiState.Closed)
        _navigateToPlayer.trySend(
            PlayerNavArgs(
                mediaId = media.id,
                episodeId = loadedPicker.episode?.id,
                resumeMs = null,
                preferredSource = candidate
            )
        )
    }
}