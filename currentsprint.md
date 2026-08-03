# OneDebrid — Current Sprint

## Status
Implementation in progress. Architectural design phase complete.
Build verification complete — project compiles cleanly as of Session 14.

## Package Structure
com.onedebrid.app
├── domain/
│   ├── model/
│   └── error/
├── data/
│   ├── repository/
│   └── local/
│       ├── dao/
│       └── entity/
├── provider/
│   ├── debrid/
│   ├── metadata/
│   ├── search/
│   └── subtitle/
├── usecase/
├── coordinator/
├── ui/
│   ├── home/
│   ├── search/
│   ├── details/
│   ├── player/
│   ├── settings/
│   └── theme/
└── di/

## Completed

### Scaffold (Session 2)
- build.gradle.kts (root)
- gradle/libs.versions.toml
- settings.gradle.kts
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/res/values/strings.xml
- app/src/main/res/values/themes.xml
- gradle/wrapper/gradle-wrapper.properties + gradlew + gradlew.bat
- app/src/main/kotlin/com/onedebrid/app/OneDebridApplication.kt
- OneDebridApplication.kt — updated Session 14: injects SessionCoordinator,
  calls start() in onCreate()
- app/src/main/kotlin/com/onedebrid/app/MainActivity.kt

### Domain Models (Session 3)
- Media.kt — canonical content representation, includes MediaType enum
- Episode.kt — single TV episode, references parent Media by ID
- StreamSource.kt — resolved playable URL from debrid, includes VideoQuality enum
- SubtitleTrack.kt — subtitle file from external provider, includes SubtitleFormat enum
- UserProfile.kt — persistent user preferences, includes PlaybackPreferences,
  SubtitlePreferences, SearchPreferences, ThemePreferences
- SessionState.kt — active application state, includes PlaybackSession,
  SearchSession, PlaybackState enum
- SearchResult.kt — search output, includes StreamCandidate
- PlaybackRequest.kt — playback intent passed to Playback system
- UserProfile.kt — updated Session 7: added providerPriorities:
  Map<String, List<String>> with default emptyMap(); key is provider type
  (e.g. "debrid", "subtitle"), value is ordered list of provider IDs
  highest priority first
- WatchedItem.kt — added Session 10: lightweight local record of profile
  interaction with media; carries mediaId, episodeId, seasonNumber,
  episodeNumber, optional positionMs/durationMs/isCompleted, lastInteractedAt;
  used by PlaybackRepository instead of full Media objects

### Error Types (Session 4)
- ProviderError.kt — sealed interface for provider-layer failures; covers
  AuthenticationFailed, RateLimited, NotFound, ServiceUnavailable,
  NetworkError, ParsingError
- AppError.kt — sealed interface for application-layer failures; covers
  NoCachedStreamAvailable, StreamResolutionFailed, NotAuthenticated,
  NoNetworkConnection, AllProvidersUnavailable, LocalStorageError(cause:
  Throwable), Unknown(message: String); each case carries isRecoverable flag

### Provider Interfaces (Session 5)
- ProviderResult.kt — sealed return type (Success / Failure) used by all
  provider interfaces; lives in domain/error/
- DebridProvider.kt — verifyAccount(), checkCache(), resolveStream()
- MetadataProvider.kt — fetchMediaDetails(), fetchEpisodes(),
  resolveExternalId(); ExternalIdType enum (IMDB, TMDB, TVDB, TRAKT)
- SearchProvider.kt — search() returning ProviderResult<SearchResult>
- SubtitleProvider.kt — searchSubtitles(), downloadSubtitle(downloadUrl: String)

### Repository Interfaces (Session 6, updated Sessions 10 & 12)
- RepositoryResult.kt — sealed return type (Success / Failure); lives in
  data/repository/; uses AppError
- MediaRepository.kt — getMediaDetails(), getEpisodes(), resolveStream(),
  checkCacheStatus(), search(); search() added Session 12; profileId param
  included for future provider priority routing
- SubtitleRepository.kt — searchSubtitles(), downloadSubtitle()
- PlaybackRepository.kt — updated Session 10: all methods profile-scoped;
  returns Flow<List<WatchedItem>> instead of Flow<List<Media>>;
  added markAsCompleted(); recordPlayed() includes episode fields;
  clearHistory() takes profileId
- ProfileRepository.kt — observeProfiles(), getProfile(), createProfile()
  returns RepositoryResult<UserProfile>, updateProfile() returns
  RepositoryResult<UserProfile>, deleteProfile(), observeActiveProfile(),
  setActiveProfile(), getActiveProfile() added Session 11
- SessionRepository.kt — in-memory only; initialise() added Session 14
  (non-suspend); observeSession() returns Flow<SessionState> (not nullable —
  filters internally); startPlaybackSession(request: PlaybackRequest,
  stream: StreamSource), updatePlaybackPosition(), endPlaybackSession(),
  updateSearchSession(), clearSearchSession(), clearSession() — all suspend
- SearchRepository.kt — updated Session 10: observeSearchHistory() takes
  profileId; addSearchQuery(), removeSearchQuery(), clearSearchHistory()

### Database Entities (Session 7)
- TypeConverters.kt — class named Converters; lives in data/local/
- ProfileEntity.kt — flattened UserProfile; fromDomain() companion function;
  toDomain() instance function; columns prefixed by subsystem
  (playbackPreferredQuality, subtitlesEnabled, etc.)
- ContinueWatchingEntity.kt — per-profile playback progress; unique index
  on (profileId, mediaId); CASCADE delete
- SearchHistoryEntity.kt — per-profile search queries; unique index on
  (profileId, query); CASCADE delete
- RecentlyPlayedEntity.kt — per-profile recently viewed media; unique index
  on (profileId, mediaId); CASCADE delete
- DownloadEntity.kt — DownloadStatus enum defined in same file
- CacheEntryEntity.kt — primary key column named key; no profile foreign key

### DAOs (Session 8)
- ProfileDao.kt — observeAllProfiles(), observeActiveProfile() as
  Flow<ProfileEntity?>, getActiveProfile(), getProfileById(),
  insertProfile(), updateProfile(), deactivateAllProfiles(),
  setProfileActive(), deleteProfile(), getProfileCount()
- ContinueWatchingDao.kt — markAsCompleted() takes completedAt: Long
- SearchHistoryDao.kt — insertQuery() IGNORE, upsertQuery() REPLACE
- RecentlyPlayedDao.kt — upsertEntry() REPLACE
- DownloadDao.kt — targeted UPDATE functions for each state transition
- CacheEntryDao.kt — all expiry queries take now: Long parameter

### Database (Session 8)
- AppDatabase.kt — 6 entities, 6 DAOs, version = 1, named "onedebrid.db"

### Hilt Modules (Sessions 8 & 10)
- DatabaseModule.kt — provides AppDatabase and all 6 DAOs
- ProviderModule.kt — binds all 4 provider stubs as @Singleton
- DispatchersModule.kt — binds CoroutineDispatchers to
  DefaultCoroutineDispatchers as @Singleton
- RepositoryModule.kt — binds all 6 repository implementations as @Singleton

### Provider Stubs (Session 8)
- StubDebridProvider.kt, StubMetadataProvider.kt, StubSearchProvider.kt,
  StubSubtitleProvider.kt — all return
  ProviderResult.Failure(ProviderError.ServiceUnavailable)

### Coroutine Infrastructure (Session 10)
- CoroutineDispatchers.kt — interface with main, io, default properties;
  lives in di/
- DefaultCoroutineDispatchers.kt — production implementation; lives in di/

### Repository Implementations (Sessions 10 & 12)
- ProfileRepositoryImpl.kt — backed by ProfileDao; mapping via
  ProfileEntity.toDomain() and ProfileEntity.fromDomain()
- PlaybackRepositoryImpl.kt — backed by ContinueWatchingDao and
  RecentlyPlayedDao; maps entities to WatchedItem
- SearchRepositoryImpl.kt — backed by SearchHistoryDao; returns
  List<String> query strings
- SessionRepositoryImpl.kt — in-memory MutableStateFlow<SessionState?>;
  initialise(profile) implements SessionRepository interface (override
  added Session 14); called by SessionCoordinator.start(); no DAO
- MediaRepositoryImpl.kt — backed by MetadataProvider, DebridProvider, and
  SearchProvider stubs; search() added Session 12; translates ProviderResult
  to RepositoryResult
- SubtitleRepositoryImpl.kt — backed by SubtitleProvider stub; uses
  track.url for download

### Build Infrastructure (Session 9)
- .gitignore, gradlew, gradle.properties, .github/workflows/build.yml

### Build Verification (Sessions 9 & 10)
- Session 9: first clean build confirmed
- Session 10: build confirmed clean after all repository implementations
  and Hilt wiring complete

## Version Changes (Session 9)
- AGP: 8.9.1 → 8.13.2
- Hilt: 2.59.2 → 2.58
- KSP: 2.3.20-2.3.9 → 2.3.9
- Gradle wrapper: 8.11.1 → 8.13

### Profile Use Cases (Session 11)
- GetActiveProfileUseCase.kt — observe active profile as Flow<UserProfile>;
  return type is unwrapped (observeActiveProfile does not wrap in RepositoryResult)
- GetProfilesUseCase.kt — observe all profiles as Flow<List<UserProfile>>
- SwitchProfileUseCase.kt — switch active profile; no-op guard if already active
- CreateProfileUseCase.kt — create profile; blank name validation
- UpdateProfileUseCase.kt — update profile; blank name validation
- DeleteProfileUseCase.kt — delete profile; active profile guard

### Search Use Cases (Session 12)
- GetSearchHistoryUseCase.kt — observe search history as Flow<List<String>>;
  profile-scoped; no dispatcher injection needed
- ClearSearchHistoryUseCase.kt — clear all search history for a profile;
  suspend write on dispatchers.io
- SearchMediaUseCase.kt — saves query to history (best-effort, swallows
  failure) then delegates to mediaRepository.search(); returns
  RepositoryResult<SearchResult>; will return AllProvidersUnavailable
  until real SearchProvider exists

### Continue Watching Use Cases (Session 12)
- GetContinueWatchingUseCase.kt — observe continue watching list as
  Flow<List<WatchedItem>>; profile-scoped; no dispatcher injection needed
- RecordPlaybackUseCase.kt — record that a media item was opened; calls
  recordPlayed() only; episodeId, seasonNumber, episodeNumber nullable for
  movie compatibility; positionMs/durationMs are not parameters here —
  progress saving is a separate concern (saveProgress on PlaybackRepository)
  nullable for movie compatibility; fire-and-forget, no return value
- MarkAsCompletedUseCase.kt — mark media as completed; takes profileId and
  mediaId only; no episodeId — completion is tracked at mediaId level on
  the interface; per-episode completion would require a PlaybackRepository
  interface change
  episodeId nullable for movie compatibility
- ClearPlaybackHistoryUseCase.kt — clear all playback history for a profile

### Playback Use Cases (Session 12)
- ResolvePlaybackUseCase.kt — resolves PlaybackRequest.preferredSource
  (StreamCandidate) to a StreamSource via mediaRepository.resolveStream();
  returns NoCachedStreamAvailable if request has no candidate; no dispatcher
  injection needed as repository handles threading internally
- StartPlaybackUseCase.kt — registers resolved stream with SessionRepository
  via startPlaybackSession(); returns RepositoryResult<Unit>; try/catch
  wraps session call because failure here blocks playback and the ViewModel
  needs a structured result to act on

### Coordinators (Session 14)
- SessionCoordinator.kt — @Singleton; observes ProfileRepository
  .observeActiveProfile(), calls sessionRepository.initialise() on each
  distinct non-null emission; start() must be called once from
  OneDebridApplication.onCreate()
- PlaybackCoordinator.kt — @Singleton; owns play()/stop() workflow;
  sequences ResolvePlaybackUseCase → StartPlaybackUseCase →
  RecordPlaybackUseCase; exposes StateFlow<PlaybackState>
  (Idle/Resolving/Ready/Error); cancels prior job on new play() call
- SearchCoordinator.kt — @Singleton; owns search()/clear() workflow;
  wraps SearchMediaUseCase; exposes StateFlow<SearchState>
  (Idle/Searching/Results/Error); cancels prior job on new search() call

### DI Infrastructure (Session 14)
- ApplicationScope.kt — @Qualifier annotation; lives in di/
- ApplicationScopeModule.kt — provides @ApplicationScope CoroutineScope
  backed by SupervisorJob() + dispatchers.default; @Singleton

## Implementation Decisions

### WatchedItem introduced as lightweight playback record
PlaybackRepository returns WatchedItem rather than Media. The local database
only stores mediaId and progress data — full Media metadata lives in the
network/cache layer. Returning WatchedItem is honest about what the repository
actually owns. Callers fetch full Media separately using mediaId.

### ProfileRepository interface uses UserProfile as return type for writes
createProfile() and updateProfile() return RepositoryResult<UserProfile>
rather than RepositoryResult<Unit>. This allows callers to get the persisted
profile back in one step without a follow-up read.
getActiveProfile(): RepositoryResult<UserProfile> was added in Session 11;
observeActiveProfile() returns Flow<UserProfile> not
Flow<RepositoryResult<UserProfile>>.

### SessionRepositoryImpl uses direct MutableStateFlow.value assignment
MutableStateFlow.update {} was not resolving correctly in this environment.
All state updates use direct _session.value = _session.value?.copy(...)
assignment instead. Functionally equivalent for single-threaded session
management.

### ProviderResult → RepositoryResult translation at repository boundary
MediaRepositoryImpl and SubtitleRepositoryImpl translate ProviderError into
AppError before returning. Use Cases never see ProviderError directly.

### All repository implementations backed by stubs until real providers exist
MediaRepositoryImpl and SubtitleRepositoryImpl will return
AppError.AllProvidersUnavailable for every call until stub providers are
replaced with real implementations. This is intentional and visible.

### ResolvePlaybackUseCase and StartPlaybackUseCase are deliberately separate
Resolution (candidate → StreamSource) and session start are kept as two
distinct Use Cases. This preserves the ability to show the user a resolved
source list before committing to playback, and matches the Smart Defaults
flow where auto-resolution can skip the selection step entirely.

### search() added to MediaRepository rather than a new repository
Search execution belongs to MediaRepository — it already owns metadata and
stream resolution. SearchRepository owns history persistence only. Adding
search() to MediaRepository keeps the boundary clean without introducing
a new interface.

### SessionRepository interface written without checking impl first (Session 14)
When adding initialise() to the SessionRepository interface, the interface
was written independently rather than checked against the existing
SessionRepositoryImpl. This caused a second build failure — suspend
modifiers and startPlaybackSession's signature didn't match between
interface and impl. Fixed by conforming the interface to the impl's
already-working design rather than the reverse. Lesson: when changing an
interface that already has an implementation, read the implementation
file first, every time — no exceptions.

## Open TODOs
- OneDebridTheme needs static fallback colour scheme for API 26-30
- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses Map<String, String> for filters;
  revisit if SearchFilters promoted to domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation — semantically incorrect; revisit when
  error model gets a review pass
- StartPlaybackUseCase uses fully qualified AppError reference inline;
  tidy to a top-level import if preferred

## Next Steps
1. ViewModels — search or profile management as natural first candidates
   since both are fully local and don't depend on real providers
2. Before further Coordinator or Repository changes: verify Media.id and
   Episode.id usage is consistent everywhere they're referenced

## Key Version Numbers
- AGP: 8.13.2
- Kotlin: 2.3.20
- KSP: 2.3.9
- Hilt: 2.58
- Compose BOM: 2026.04.01
- Room: 2.7.1
- Coil: 3.1.0 (group: io.coil-kt.coil3)
- Media3: 1.6.1
- Navigation Compose: 2.9.8
- DataStore: 1.2.1
- Gradle wrapper: 8.13
- Material: 1.12.0