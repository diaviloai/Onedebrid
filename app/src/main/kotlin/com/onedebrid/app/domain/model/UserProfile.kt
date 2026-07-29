package com.onedebrid.app.domain.model

/**
 * The persistent representation of a user profile.
 *
 * UserProfile stores all preferences that should survive application restarts.
 * It never stores temporary state — that belongs to SessionState.
 *
 * Preferences are grouped into focused objects so that each subsystem receives
 * only the slice of preferences it needs, rather than the entire profile.
 *
 * providerPriorities: Maps provider type (e.g. "debrid", "metadata", "search",
 * "subtitle") to an ordered list of provider IDs, highest priority first.
 * An empty list for a given type means no explicit priority — the coordinator
 * uses whatever registered providers are available.
 * An absent key is equivalent to an empty list.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitles: SubtitlePreferences = SubtitlePreferences(),
    val search: SearchPreferences = SearchPreferences(),
    val theme: ThemePreferences = ThemePreferences(),
    val providerPriorities: Map<String, List<String>> = emptyMap()
)

/**
 * Playback-related preferences.
 *
 * preferredQuality: The quality the user prefers when multiple stream sources
 * are available. Smart Defaults uses this to auto-select without prompting.
 *
 * preferredAudioLanguage: BCP-47 language code. Used to auto-select the correct
 * audio track when a stream contains multiple.
 *
 * autoPlay: Whether the next episode begins automatically after the current
 * one finishes.
 */
data class PlaybackPreferences(
    val preferredQuality: VideoQuality = VideoQuality.HD_1080,
    val preferredAudioLanguage: String = "en",
    val autoPlay: Boolean = true
)

/**
 * Subtitle-related preferences.
 *
 * enabled: Master toggle. When false, no subtitle track is attached regardless
 * of other settings.
 *
 * preferredLanguageCode: BCP-47 language code used for automatic subtitle
 * selection.
 *
 * preferredFormat: The subtitle format to prefer when multiple are available.
 * Null means no format preference.
 *
 * hearingImpaired: When true, hearing-impaired tracks are preferred. When false,
 * they are excluded unless no alternative exists.
 */
data class SubtitlePreferences(
    val enabled: Boolean = true,
    val preferredLanguageCode: String = "en",
    val preferredFormat: SubtitleFormat? = null,
    val hearingImpaired: Boolean = false
)

/**
 * Search-related preferences.
 *
 * includeAdult: Whether adult content appears in search results.
 *
 * preferredContentLanguage: BCP-47 language code used to filter or rank
 * metadata results by original language where supported.
 */
data class SearchPreferences(
    val includeAdult: Boolean = false,
    val preferredContentLanguage: String = "en"
)

/**
 * Theme-related preferences.
 *
 * useDynamicColor: Whether Material You dynamic color is applied. When false,
 * the static fallback palette is used instead.
 *
 * darkMode: Explicit dark mode override. Null means follow the system setting.
 */
data class ThemePreferences(
    val useDynamicColor: Boolean = true,
    val darkMode: Boolean? = null
)