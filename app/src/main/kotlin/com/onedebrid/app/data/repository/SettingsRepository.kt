package com.onedebrid.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val RD_API_KEY = stringPreferencesKey("rd_api_key")
        private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    }

    val rdApiKeyFlow: Flow<String> = context.dataStore.data.map { it[RD_API_KEY] ?: "" }
    val tmdbApiKeyFlow: Flow<String> = context.dataStore.data.map { it[TMDB_API_KEY] ?: "" }

    // Synchronous reads for OkHttp Interceptors
    suspend fun getRdApiKey(): String = context.dataStore.data.first()[RD_API_KEY] ?: ""
    suspend fun getTmdbApiKey(): String = context.dataStore.data.first()[TMDB_API_KEY] ?: ""

    suspend fun setRdApiKey(key: String) {
        context.dataStore.edit { it[RD_API_KEY] = key }
    }

    suspend fun setTmdbApiKey(key: String) {
        context.dataStore.edit { it[TMDB_API_KEY] = key }
    }
}