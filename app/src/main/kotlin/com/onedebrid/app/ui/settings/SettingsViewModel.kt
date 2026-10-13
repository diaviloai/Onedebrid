package com.onedebrid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onedebrid.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val rdApiKey: StateFlow<String> = settingsRepository.rdApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val tmdbApiKey: StateFlow<String> = settingsRepository.tmdbApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun updateRdApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setRdApiKey(key) }
    }

    fun updateTmdbApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setTmdbApiKey(key) }
    }
}