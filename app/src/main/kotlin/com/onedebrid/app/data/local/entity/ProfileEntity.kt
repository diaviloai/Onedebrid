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

/**
 * Database entity representing a stored user profile.
 *
 * ProfileEntity is the Room representation of UserProfile. It is not the same
 * class. The repository layer converts between the two — entities never leave
 * the data layer, and domain models never enter the database directly.
 *
 * Nested preference objects from UserProfile are flattened into individual
 * columns. This keeps the schema readable and avoids opaque JSON blobs for
 * fields that may eventually need to be queried individually.
 *
 * providerPriorities is the exception: it is stored as JSON because its
 * structure is variable by design and querying on individual provider IDs
 * directly in SQL is not a current requirement.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(

    @PrimaryKey
    val id: String,

    val name: String,
    
    val isActive: Boolean = false,

val createdAt: Long,

    val isDefault: Boolean,

    // PlaybackPreferences
    val playbackPreferredQuality: VideoQuality,
    val playbackPreferredAudioLanguage: String,
    val playbackAutoPlay: Boolean,

    // SubtitlePreferences
    val subtitlesEnabled: Boolean,
    val subtitlesPreferredLanguageCode: String,
    val subtitlesPreferredFormat: SubtitleFormat?,
    val subtitlesHearingImpaired: Boolean,

    // SearchPreferences
    val searchIncludeAdult: Boolean,
    val searchPreferredContentLanguage: String,

    // ThemePreferences
    val themeUseDynamicColor: Boolean,
    val themeDarkMode: Boolean?,

    // Provider priorities — stored as JSON via TypeConverters
    val providerPriorities: Map<String, List<String>>
) {

    /**
     * Converts this database entity into the UserProfile domain model.
     *
     * Called by the repository when reading a profile from the database.
     * The domain model is what the rest of the application works with.
     */
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

        /**
         * Converts a UserProfile domain model into a ProfileEntity for storage.
         *
         * Called by the repository when writing a profile to the database.
         * Lives in the companion object so it can be called without an existing
         * entity instance: ProfileEntity.fromDomain(profile)
         */
        fun fromDomain(profile: UserProfile): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
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