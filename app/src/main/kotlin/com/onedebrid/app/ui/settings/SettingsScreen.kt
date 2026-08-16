package com.onedebrid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onedebrid.app.R
import com.onedebrid.app.domain.error.AppError
import com.onedebrid.app.domain.model.PlaybackPreferences
import com.onedebrid.app.domain.model.SearchPreferences
import com.onedebrid.app.domain.model.SubtitleFormat
import com.onedebrid.app.domain.model.SubtitlePreferences
import com.onedebrid.app.domain.model.ThemePreferences
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.VideoQuality
import java.util.UUID

/**
 * The Settings / Profile management screen.
 *
 * Two responsibilities, per Project_Design.md's Profile System and
 * UI_UX_Design.md's "Settings & Profiles" destination:
 *
 * 1. Profile list — switch active profile, create a new one, rename or
 *    delete an existing one.
 * 2. Preference editing for the active profile — all four groups on
 *    UserProfile (playback, subtitles, search, theme). Editing targets the
 *    active profile only; there is no per-profile "select which profile
 *    you're editing" concept separate from switching, since Smart Defaults
 *    (Project_Design.md) always apply to whichever profile is active.
 *
 * State handling: ProfileViewModel.uiState is Loading/Ready, and effects
 * (ProfileCreated/Updated/Deleted/Error) are transient one-offs consumed via
 * LaunchedEffect + effectConsumed(), per the Unidirectional Data Flow /
 * UIEffect pattern in Technical_standards.md.
 *
 * ID generation: CreateProfileUseCase and ProfileRepositoryImpl.createProfile
 * pass UserProfile.id straight through to Room's @PrimaryKey column with no
 * generation step anywhere in that chain (verified by reading
 * ProfileRepositoryImpl.kt and ProfileEntity.kt directly before writing this
 * screen — see the project's "never write against assumed interfaces"
 * principle). This screen is therefore responsible for generating a real ID
 * via UUID.randomUUID() before calling createProfile(); passing an empty or
 * fixed string would either silently misbehave or collide across multiple
 * new-profile creations.
 *
 * Icon usage note: this screen deliberately avoids introducing any new
 * Icons.Filled.* symbol beyond Icons.Filled.Close (already proven to
 * compile via HomeScreen.kt in this same session). Session 22's build
 * failure came from assuming a Material Icons symbol was available without
 * checking the declared dependency; this screen sidesteps that risk
 * entirely by using text buttons/labels instead of icons for all new
 * actions (add/delete/rename/switch).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val effect by viewModel.effect.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Consume one-off effects. Only Error needs UI action here (a dialog);
    // Created/Updated/Deleted require no explicit feedback beyond the
    // state list itself updating, since the underlying Flows already push
    // a fresh Ready state.
    LaunchedEffect(effect) {
        val currentEffect = effect
        if (currentEffect is ProfileEffect.Error) {
            pendingError = currentEffect.error
        }
        if (currentEffect != null) {
            viewModel.effectConsumed()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        when (val state = uiState) {
            is ProfileUiState.Loading -> LoadingContent()
            is ProfileUiState.Ready -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        ProfilesSection(
                            profiles = state.profiles,
                            activeProfile = state.activeProfile,
                            onSwitch = viewModel::switchProfile,
                            onRename = { profile -> renamingProfile = profile },
                            onDelete = { profileId -> viewModel.deleteProfile(profileId) },
                            onAddProfile = { showCreateDialog = true }
                        )
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                    item {
                        PreferencesSection(
                            activeProfile = state.activeProfile,
                            onProfileUpdated = viewModel::updateProfile
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ProfileNameDialog(
            titleRes = R.string.settings_new_profile_title,
            initialName = "",
            onConfirm = { name ->
                viewModel.createProfile(
                    UserProfile(id = UUID.randomUUID().toString(), name = name)
                )
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renamingProfile?.let { profile ->
        ProfileNameDialog(
            titleRes = R.string.settings_rename_profile_title,
            initialName = profile.name,
            onConfirm = { newName ->
                viewModel.updateProfile(profile.copy(name = newName))
                renamingProfile = null
            },
            onDismiss = { renamingProfile = null }
        )
    }

    pendingError?.let { error ->
        AlertDialog(
            onDismissRequest = { pendingError = null },
            confirmButton = {
                TextButton(onClick = { pendingError = null }) {
                    Text(stringResource(R.string.settings_profile_cancel))
                }
            },
            text = { Text(settingsErrorMessage(error)) }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ── Profiles Section ────────────────────────────────────────────────────

@Composable
private fun ProfilesSection(
    profiles: List<UserProfile>,
    activeProfile: UserProfile,
    onSwitch: (String) -> Unit,
    onRename: (UserProfile) -> Unit,
    onDelete: (String) -> Unit,
    onAddProfile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_profiles_title),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onAddProfile) {
                Text(stringResource(R.string.settings_profile_add))
            }
        }

        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                isActive = profile.id == activeProfile.id,
                canDelete = profile.id != activeProfile.id,
                onSwitch = { onSwitch(profile.id) },
                onRename = { onRename(profile) },
                onDelete = { onDelete(profile.id) }
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: UserProfile,
    isActive: Boolean,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
            if (isActive) {
                Text(
                    text = stringResource(R.string.settings_profile_active_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Row {
            if (!isActive) {
                TextButton(onClick = onSwitch) {
                    Text(stringResource(R.string.settings_profile_switch))
                }
            }
            TextButton(onClick = onRename) {
                Text(stringResource(R.string.settings_profile_save))
            }
            if (canDelete) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.settings_profile_delete))
                }
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(
    titleRes: Int,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_profile_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.settings_profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_profile_cancel))
            }
        }
    )
}

// ── Preferences Section ─────────────────────────────────────────────────

@Composable
private fun PreferencesSection(
    activeProfile: UserProfile,
    onProfileUpdated: (UserProfile) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PlaybackPreferencesSection(
            preferences = activeProfile.playback,
            onChange = { updated -> onProfileUpdated(activeProfile.copy(playback = updated)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SubtitlePreferencesSection(
            preferences = activeProfile.subtitles,
            onChange = { updated -> onProfileUpdated(activeProfile.copy(subtitles = updated)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SearchPreferencesSection(
            preferences = activeProfile.search,
            onChange = { updated -> onProfileUpdated(activeProfile.copy(search = updated)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ThemePreferencesSection(
            preferences = activeProfile.theme,
            onChange = { updated -> onProfileUpdated(activeProfile.copy(theme = updated)) }
        )
    }
}
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A single-line free-text field for a BCP-47 language code.
 *
 * A free-text field rather than a fixed dropdown of languages is
 * deliberate: BCP-47 covers a large, open-ended set of codes, and no
 * language list/lookup exists anywhere in the codebase yet to validate or
 * present one. The hint text communicates the expected format. Revisit if
 * a proper language picker becomes worth building later.
 */
@Composable
private fun LanguageCodeField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.settings_language_code_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun <T> DropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(optionLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackPreferencesSection(
    preferences: PlaybackPreferences,
    onChange: (PlaybackPreferences) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_playback))

        DropdownField(
            label = stringResource(R.string.settings_preferred_quality),
            selected = preferences.preferredQuality,
            options = VideoQuality.entries.filter { it != VideoQuality.UNKNOWN },
            optionLabel = { it.name },
            onSelect = { onChange(preferences.copy(preferredQuality = it)) }
        )

        LanguageCodeField(
            label = stringResource(R.string.settings_preferred_audio_language),
            value = preferences.preferredAudioLanguage,
            onValueChange = { onChange(preferences.copy(preferredAudioLanguage = it)) }
        )

        SwitchRow(
            label = stringResource(R.string.settings_autoplay),
            checked = preferences.autoPlay,
            onCheckedChange = { onChange(preferences.copy(autoPlay = it)) }
        )
    }
}

@Composable
private fun SubtitlePreferencesSection(
    preferences: SubtitlePreferences,
    onChange: (SubtitlePreferences) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_subtitles))

        SwitchRow(
            label = stringResource(R.string.settings_subtitles_enabled),
            checked = preferences.enabled,
            onCheckedChange = { onChange(preferences.copy(enabled = it)) }
        )

        LanguageCodeField(
            label = stringResource(R.string.settings_subtitle_language),
            value = preferences.preferredLanguageCode,
            onValueChange = { onChange(preferences.copy(preferredLanguageCode = it)) }
        )

        val noPreferenceLabel = stringResource(R.string.settings_subtitle_format_none)
        DropdownField(
            label = stringResource(R.string.settings_subtitle_format),
            selected = preferences.preferredFormat,
            options = listOf(null) + SubtitleFormat.entries,
            optionLabel = { it?.name ?: noPreferenceLabel },
            onSelect = { onChange(preferences.copy(preferredFormat = it)) }
        )

        SwitchRow(
            label = stringResource(R.string.settings_hearing_impaired),
            checked = preferences.hearingImpaired,
            onCheckedChange = { onChange(preferences.copy(hearingImpaired = it)) }
        )
    }
}

@Composable
private fun SearchPreferencesSection(
    preferences: SearchPreferences,
    onChange: (SearchPreferences) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_search))

        SwitchRow(
            label = stringResource(R.string.settings_include_adult),
            checked = preferences.includeAdult,
            onCheckedChange = { onChange(preferences.copy(includeAdult = it)) }
        )

        LanguageCodeField(
            label = stringResource(R.string.settings_content_language),
            value = preferences.preferredContentLanguage,
            onValueChange = { onChange(preferences.copy(preferredContentLanguage = it)) }
        )
    }
}

@Composable
private fun ThemePreferencesSection(
    preferences: ThemePreferences,
    onChange: (ThemePreferences) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.settings_section_theme))

        SwitchRow(
            label = stringResource(R.string.settings_dynamic_color),
            checked = preferences.useDynamicColor,
            onCheckedChange = { onChange(preferences.copy(useDynamicColor = it)) }
        )

        val systemLabel = stringResource(R.string.settings_dark_mode_system)
        val onLabel = stringResource(R.string.settings_dark_mode_on)
        val offLabel = stringResource(R.string.settings_dark_mode_off)
        DropdownField(
            label = stringResource(R.string.settings_dark_mode),
            selected = preferences.darkMode,
            options = listOf(null, true, false),
            optionLabel = {
                when (it) {
                    null -> systemLabel
                    true -> onLabel
                    false -> offLabel
                }
            },
            onSelect = { onChange(preferences.copy(darkMode = it)) }
        )
    }
}

/**
 * User-facing copy for AppError cases relevant to profile operations.
 *
 * Matches SearchScreen's and PlayerScreen's local errorMessage() convention
 * — AppError stays presentation-agnostic (Technical_standards.md).
 * LocalStorageError's cause is deliberately not surfaced (AppError.kt's own
 * doc comment: "Not shown to the user directly"), including the blank-name
 * validation case from CreateProfileUseCase/UpdateProfileUseCase and the
 * "cannot delete active profile" case from DeleteProfileUseCase — both
 * currently arrive as LocalStorageError per the Open TODOs note that
 * AppError has no ValidationError case yet. A generic message is shown
 * either way; this is a known, tracked limitation, not something this
 * screen works around.
 */
@Composable
private fun settingsErrorMessage(error: AppError): String = when (error) {
    is AppError.LocalStorageError -> stringResource(R.string.settings_error_generic)
    is AppError.Unknown -> stringResource(R.string.settings_error_generic)
    is AppError.NoNetworkConnection -> stringResource(R.string.player_error_no_network)
    is AppError.AllProvidersUnavailable -> stringResource(R.string.search_error_providers_unavailable)
    is AppError.NotAuthenticated -> stringResource(R.string.player_error_not_authenticated)
    is AppError.NoCachedStreamAvailable -> stringResource(R.string.settings_error_generic)
    is AppError.StreamResolutionFailed -> stringResource(R.string.settings_error_generic)
}