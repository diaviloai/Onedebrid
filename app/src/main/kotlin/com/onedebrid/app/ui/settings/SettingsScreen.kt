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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val effect by profileViewModel.effect.collectAsState()

    val rdApiKey by settingsViewModel.rdApiKey.collectAsState()
    val tmdbApiKey by settingsViewModel.tmdbApiKey.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var pendingError by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(effect) {
        val currentEffect = effect
        if (currentEffect is ProfileEffect.Error) {
            pendingError = currentEffect.error
        }
        if (currentEffect != null) {
            profileViewModel.effectConsumed()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        when (val state = uiState) {
            is ProfileUiState.Loading -> LoadingContent()
            is ProfileUiState.Ready -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    
                    item {
                        ApiKeysSection(
                            rdApiKey = rdApiKey,
                            tmdbApiKey = tmdbApiKey,
                            onRdApiKeyChange = settingsViewModel::updateRdApiKey,
                            onTmdbApiKeyChange = settingsViewModel::updateTmdbApiKey
                        )
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                    item {
                        ProfilesSection(
                            profiles = state.profiles,
                            activeProfile = state.activeProfile,
                            onSwitch = profileViewModel::switchProfile,
                            onRename = { profile -> renamingProfile = profile },
                            onDelete = { profileId -> profileViewModel.deleteProfile(profileId) },
                            onAddProfile = { showCreateDialog = true }
                        )
                    }

                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                    item {
                        PreferencesSection(
                            activeProfile = state.activeProfile,
                            onProfileUpdated = profileViewModel::updateProfile
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
                profileViewModel.createProfile(
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
                profileViewModel.updateProfile(profile.copy(name = newName))
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

// ── API Keys Section ────────────────────────────────────────────────────

@Composable
private fun ApiKeysSection(
    rdApiKey: String,
    tmdbApiKey: String,
    onRdApiKeyChange: (String) -> Unit,
    onTmdbApiKeyChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("API Keys")

        OutlinedTextField(
            value = rdApiKey,
            onValueChange = onRdApiKeyChange,
            label = { Text("Real-Debrid API Key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        OutlinedTextField(
            value = tmdbApiKey,
            onValueChange = onTmdbApiKeyChange,
            label = { Text("TMDB API Key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
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