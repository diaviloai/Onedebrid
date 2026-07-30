package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.onedebrid.app.domain.model.SubtitleFormat
import com.onedebrid.app.domain.model.UserProfile
import com.onedebrid.app.domain.model.PlaybackPreferences
import com.onedebrid.app.domain.model.SubtitlePreferences
import com.onedebrid.app.domain.model.SearchPreferences
import com.onedebrid.app.domain.model.ThemePreferences
import com.onedebrid.app.domain.model.VideoQuality

@Entity(tableName = "profiles")
data class ProfileEntity(

    @PrimaryKey
    val id: String,

    val name: String,

    val isActive: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    val isDefault: Boolean,

    val playbackPreferredQuality: VideoQuality,
    val playbackPreferredAudioLanguage: String,
    val playbackAutoPlay: Boolean,

    val subtitlesEnabled: Boolean,
    val subtitlesPreferredLanguageCode: String,
    val subtitlesPreferredFormat: SubtitleFormat?,
    val subtitlesHearingImpaired: Boolean,

    val searchIncludeAdult: Boolean,
    val searchPreferredContentLanguage: String,

    val themeUseDynamicColor: Boolean,
    val themeDarkMode: Boolean?,

    val providerPriorities: Map<String, List<String>>
) {

    fun toDomain(): UserProfile = UserProfile(
        id = id,
        name = name,
        isDefault = isDefault,
        playback = PlaybackPreferences(
            preferredQuality = playbackPreferredQuality,
            preferredAudioLanguage = playbackPreferredAudioLanguage,
            autoPlay = playbackAutoPlay
        ),
        subtitles = SubtitlePreferences(
            enabled = subtitlesEnabled,
            preferredLanguageCode = subtitlesPreferredLanguageCode,
            preferredFormat = subtitlesPreferredFormat,
            hearingImpaired = subtitlesHearingImpaired
        ),
        search = SearchPreferences(
            includeAdult = searchIncludeAdult,
            preferredContentLanguage = searchPreferredContentLanguage
        ),
        theme = ThemePreferences(
            useDynamicColor = themeUseDynamicColor,
            darkMode = themeDarkMode
        ),
        providerPriorities = providerPriorities
    )

    companion object {

        fun fromDomain(
            profile: UserProfile,
            isActive: Boolean = false,
            createdAt: Long = System.currentTimeMillis()
        ): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            isActive = isActive,
            createdAt = createdAt,
            isDefault = profile.isDefault,
            playbackPreferredQuality = profile.playback.preferredQuality,
            playbackPreferredAudioLanguage = profile.playback.preferredAudioLanguage,
            playbackAutoPlay = profile.playback.autoPlay,
            subtitlesEnabled = profile.subtitles.enabled,
            subtitlesPreferredLanguageCode = profile.subtitles.preferredLanguageCode,
            subtitlesPreferredFormat = profile.subtitles.preferredFormat,
            subtitlesHearingImpaired = profile.subtitles.hearingImpaired,
            searchIncludeAdult = profile.search.includeAdult,
            searchPreferredContentLanguage = profile.search.preferredContentLanguage,
            themeUseDynamicColor = profile.theme.useDynamicColor,
            themeDarkMode = profile.theme.darkMode,
            providerPriorities = profile.providerPriorities
        )
    }
}