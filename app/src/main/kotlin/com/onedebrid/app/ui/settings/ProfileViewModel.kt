package com.onedebrid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.RepositoryResult
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.usecase.CreateProfileUseCase
import com.onedebrid.app.usecase.DeleteProfileUseCase
import com.onedebrid.app.usecase.GetActiveProfileUseCase
import com.onedebrid.app.usecase.GetProfilesUseCase
import com.onedebrid.app.usecase.SwitchProfileUseCase
import com.onedebrid.app.usecase.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for profile management.
 *
 * Exposes the list of all profiles and the currently active profile
 * as a single combined UiState. Provides functions for all profile
 * operations — create, update, delete, switch.
 *
 * Does not import or reference any repository or database entity directly.
 * All data access goes through Use Cases.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val createProfileUseCase: CreateProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val switchProfileUseCase: SwitchProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /**
     * One-off effects (navigation, snackbars) that should not survive
     * configuration changes. The UI collects this as a Flow of events.
     */
    private val _effect = MutableStateFlow<ProfileEffect?>(null)
    val effect: StateFlow<ProfileEffect?> = _effect.asStateFlow()

    init {
        // Combine the profiles list and the active profile into one state update.
        // Both are Flows — whenever either changes, the UI gets a fresh snapshot.
        combine(
            getProfilesUseCase(),
            getActiveProfileUseCase()
        ) { profiles, activeProfile ->
            ProfileUiState.Ready(
                profiles = profiles,
                activeProfile = activeProfile
            )
        }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun createProfile(profile: UserProfile) {
        viewModelScope.launch {
            when (val result = createProfileUseCase(profile)) {
                is RepositoryResult.Success -> {
                    _effect.value = ProfileEffect.ProfileCreated(result.data)
                }
                is RepositoryResult.Failure -> {
                    _effect.value = ProfileEffect.Error(result.error)
                }
            }
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            when (val result = updateProfileUseCase(profile)) {
                is RepositoryResult.Success -> {
                    _effect.value = ProfileEffect.ProfileUpdated(result.data)
                }
                is RepositoryResult.Failure -> {
                    _effect.value = ProfileEffect.Error(result.error)
                }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            when (val result = deleteProfileUseCase(profileId)) {
                is RepositoryResult.Success -> {
                    _effect.value = ProfileEffect.ProfileDeleted
                }
                is RepositoryResult.Failure -> {
                    _effect.value = ProfileEffect.Error(result.error)
                }
            }
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            when (val result = switchProfileUseCase(profileId)) {
                is RepositoryResult.Success -> {
                    // No effect needed — the active profile Flow updates automatically,
                    // which will push a new Ready state to the UI.
                }
                is RepositoryResult.Failure -> {
                    _effect.value = ProfileEffect.Error(result.error)
                }
            }
        }
    }

    /** Called by the UI after consuming an effect, to clear it. */
    fun effectConsumed() {
        _effect.value = null
    }
}

// ── UI State ──────────────────────────────────────────────────────────────────

/**
 * The complete rendering state for the profile management screen.
 */
sealed interface ProfileUiState {

    /** Waiting for the first emission from the profile Flows. */
    data object Loading : ProfileUiState

    /**
     * Profiles and active profile are both available.
     *
     * profiles: All profiles in the database, in display order.
     * activeProfile: The profile that is currently active.
     */
    data class Ready(
        val profiles: List<UserProfile>,
        val activeProfile: UserProfile
    ) : ProfileUiState
}

// ── One-off Effects ───────────────────────────────────────────────────────────

/**
 * Transient events that represent one-off actions rather than persistent state.
 *
 * The UI consumes an effect and calls effectConsumed() to clear it.
 * Using StateFlow<ProfileEffect?> (null = no pending effect) is simpler
 * than Channel for this use case, because profile operations don't generate
 * effects at high frequency.
 */
sealed interface ProfileEffect {
    data class ProfileCreated(val profile: UserProfile) : ProfileEffect
    data class ProfileUpdated(val profile: UserProfile) : ProfileEffect
    data object ProfileDeleted : ProfileEffect
    data class Error(val error: AppError) : ProfileEffect
}