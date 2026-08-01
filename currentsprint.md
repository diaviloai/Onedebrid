# OneDebrid — Current Sprint

## Status
Implementation in progress. Architectural design phase complete.
Build verification complete — project compiles cleanly as of Session 9.

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

### Error Types (Session 4)
- ProviderError.kt — sealed interface for provider-layer failures; covers
  AuthenticationFailed, RateLimited, NotFound, ServiceUnavailable,
  NetworkError, ParsingError
- AppError.kt — sealed interface for application-layer failures; covers
  NoCachedStreamAvailable, StreamResolutionFailed, NotAuthenticated,
  NoNetworkConnection, AllProvidersUnavailable, LocalStorageError, Unknown;
  each case carries an isRecoverable flag used by the UI presentation layer

### Provider Interfaces (Session 5)
- ProviderResult.kt — sealed return type (Success / Failure) used by all
  provider interfaces; lives in domain/error/; replaces nullable returns and
  avoids Kotlin Result<T>; includes asSuccess() and asFailure() extensions
- DebridProvider.kt — contract for debrid services; defines verifyAccount(),
  checkCache(), resolveStream(); includes AccountInfo data class and
  displayName property
- MetadataProvider.kt — contract for metadata enrichment; defines
  fetchMediaDetails(), fetchEpisodes(), resolveExternalId(); includes
  ExternalIdType enum (IMDB, TMDB, TVDB, TRAKT); fetchMediaDetails and
  fetchEpisodes both take externalId + idType parameters; resolveExternalId
  takes sourceId, sourceType, targetType; displayName property required
- SearchProvider.kt — contract for search/scraper integrations; defines
  search() returning ProviderResult<SearchResult> (singular, not List);
  includes SearchFilters data class; displayName property required
- SubtitleProvider.kt — contract for subtitle services; defines
  searchSubtitles(), downloadSubtitle(downloadUrl: String); includes
  SubtitleQuery data class; displayName property required

### Repository Interfaces (Session 6)
- RepositoryResult.kt — sealed return type (Success / Failure) for repository
  one-shot operations; uses AppError at the application layer, distinct from
  ProviderResult which uses ProviderError at the provider layer
- MediaRepository.kt — media metadata and stream resolution; covers
  getMediaDetails(), getEpisodes(), resolveStream(), checkCacheStatus()
- SubtitleRepository.kt — subtitle search and download; covers
  searchSubtitles(), downloadSubtitle()
- PlaybackRepository.kt — Continue Watching, playback progress, recently
  played; Flow-based observation for list screens, suspend functions for
  writes; progress stored by mediaId + optional episodeId
- ProfileRepository.kt — profile CRUD and active profile tracking;
  observeActiveProfile() returns non-nullable Flow<UserProfile>; deleteProfile
  rejects deletion of the active profile
- SessionRepository.kt — in-memory session state only; not persisted; owned
  by Session System; reset on profile switch
- SearchRepository.kt — search history persistence per profile; does not
  execute searches; supports add, remove, clear

### Implementation Decisions (Session 6)
- RepositoryResult<T> introduced as a repository-layer return type distinct
  from ProviderResult<T>; repositories translate ProviderError into AppError
  before returning, so callers in the Use Case layer never see ProviderError
- SessionRepository holds no persistent state by design; if specific session
  data later earns persistence, the interface absorbs the change transparently
- SearchRepository.updateSearchSession uses Map<String, String> for filters
  rather than SearchFilters to avoid pulling a provider-layer type into the
  data layer; revisit if SearchFilters is promoted to a domain model
- observeActiveProfile() is non-nullable; the app guarantees at least one
  profile exists after first launch; null would represent a bug, not a state

### Database Entities (Session 7)
- TypeConverters.kt — Room TypeConverters for complex types; class named
  Converters; covers Map<String, List<String>> for providerPriorities,
  List<String>, VideoQuality enum, SubtitleFormat enum (nullable),
  DownloadStatus enum; lives in data/local/
- ProfileEntity.kt — flattened UserProfile; columns include isActive (Boolean,
  default false), createdAt (Long, default System.currentTimeMillis()),
  isDefault; fromDomain() takes optional isActive and createdAt parameters;
  all nested preference objects stored as individual columns; providerPriorities
  stored as JSON via TypeConverter
- ContinueWatchingEntity.kt — per-profile playback progress; stores
  positionMs, durationMs, lastWatchedAt, isCompleted; episodic fields
  nullable; unique index on (profileId, mediaId); CASCADE delete on profile
- SearchHistoryEntity.kt — per-profile search queries; stores query string
  and searchedAt timestamp; unique index on (profileId, query); CASCADE delete
- RecentlyPlayedEntity.kt — per-profile recently viewed media; stores
  mediaId and lastPlayedAt; episodic fields nullable; unique index on
  (profileId, mediaId); CASCADE delete
- DownloadEntity.kt — tracks offline media; status state machine
  (QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED) via
  DownloadStatus enum defined in same file; timestamp column named createdAt;
  localPath nullable until file created; retryCount for retry limiting
- CacheEntryEntity.kt — generic type-discriminated cache store; primary key
  column named key (not cacheKey); cacheType column for bulk operations; data
  stored as opaque JSON string; expiresAt enables staleness detection; no
  profile foreign key

### DAOs (Session 8)
- ProfileDao.kt — observeAllProfiles() orders by createdAt ASC,
  observeActiveProfile() filters isActive=1 as Flow<ProfileEntity?>,
  getActiveProfile(), getProfileById(), insertProfile() with REPLACE,
  updateProfile(), deactivateAllProfiles(), setProfileActive(),
  deleteProfile(), getProfileCount(); lives in data/local/dao/
- ContinueWatchingDao.kt — observeContinueWatching() filters isCompleted=0
  ordered by lastWatchedAt DESC with configurable limit,
  observeProgressForMedia(), getProgressForMedia(), upsertProgress() with
  REPLACE, markAsCompleted() targeted SQL update, removeEntry(),
  clearAllForProfile(); lives in data/local/dao/
- SearchHistoryDao.kt — observeSearchHistory() with configurable limit,
  insertQuery() with IGNORE, upsertQuery() with REPLACE, removeQuery(),
  clearHistoryForProfile(); lives in data/local/dao/
- RecentlyPlayedDao.kt — observeRecentlyPlayed() includes completed items,
  getEntryForMedia(), upsertEntry() with REPLACE, removeEntry(),
  clearAllForProfile(); lives in data/local/dao/
- DownloadDao.kt — observeAllDownloads() orders by createdAt DESC,
  observeActiveDownloads() filters IN (QUEUED, DOWNLOADING, PAUSED) orders
  by createdAt ASC, observeDownload(), getDownload(), getFailedDownloads(),
  getCompletedDownloads(), insertDownload() with IGNORE, targeted UPDATE
  functions for each state transition, deleteDownload(); lives in data/local/dao/
- CacheEntryDao.kt — no Flow observation; getEntry() and getEntriesForType()
  filter on column named key (not cacheKey); both take now: Long for testable
  expiry; expiresAt IS NULL treated as never-expires; upsertEntry() with
  REPLACE, deleteEntry(), pruneExpired(), clearType(), clearAll();
  lives in data/local/dao/

### Database (Session 8)
- AppDatabase.kt — registers all 6 entities and 6 DAOs; version = 1;
  exportSchema = true; @TypeConverters(Converters::class); named "onedebrid.db"
- app/build.gradle.kts — ksp { arg("room.schemaLocation", ...) } inside
  android block; kotlinOptions block removed; replaced with kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

### Hilt Modules (Session 8)
- DatabaseModule.kt — @InstallIn(SingletonComponent::class) object;
  provideAppDatabase() is @Singleton using @ApplicationContext; DAO providers
  have no @Singleton; database named "onedebrid.db"; lives in di/
- ProviderModule.kt — @InstallIn(SingletonComponent::class) abstract class;
  uses @Binds; all four providers bound as @Singleton; lives in di/

### Provider Stubs (Session 8)
- StubDebridProvider.kt — implements DebridProvider; id = "stub_debrid";
  displayName = "Stub Debrid"; all methods return
  ProviderResult.Failure(ProviderError.ServiceUnavailable);
  import is com.onedebrid.app.domain.error.ProviderResult
- StubMetadataProvider.kt — implements MetadataProvider; id = "stub_metadata";
  displayName = "Stub Metadata"; method signatures match interface exactly:
  fetchMediaDetails(externalId, idType), fetchEpisodes(externalId, idType,
  season), resolveExternalId(sourceId, sourceType, targetType)
- StubSearchProvider.kt — implements SearchProvider; id = "stub_search";
  displayName = "Stub Search"; search() returns ProviderResult<SearchResult>
  singular, not List
- StubSubtitleProvider.kt — implements SubtitleProvider; id = "stub_subtitle";
  displayName = "Stub Subtitle"; downloadSubtitle takes downloadUrl: String

### Build Infrastructure (Session 9)
- .gitignore — added at project root; no *.jar exclusion so wrapper jar
  can be committed
- gradlew — Unix executable added at project root (was missing; only
  gradlew.bat existed)
- gradle.properties — added at project root; android.useAndroidX=true,
  kotlin.code.style=official, org.gradle.caching=true,
  org.gradle.configuration-cache=true, org.gradle.jvmargs=-Xmx2048m
- .github/workflows/build.yml — CI workflow using ubuntu-latest, JDK 17
  temurin, gradle/actions/setup-gradle@v3 with gradle-version 8.13,
  regenerates wrapper via gradle wrapper --gradle-version=8.13
- com.google.android.material:material:1.12.0 added to dependencies —
  required for Theme.Material3.DayNight.NoActionBar in themes.xml
- AndroidManifest.xml — icon and roundIcon changed from @mipmap/ic_launcher
  to @android:drawable/sym_def_app_icon (placeholder until real icons added)

### Build Verification (Session 9)
- First clean build confirmed successful — 3m 49s, all 31 tasks executed
- KSP annotation processing confirmed working (Room DAOs, Hilt modules)
- All entities, DAOs, TypeConverters, AppDatabase, and Hilt modules
  verified by compiler

## Version Changes Made During Build Verification (Session 9)
- AGP: 8.9.1 → 8.13.2 (required by KSP 2.3.9 minimum AGP 8.10.0)
- Hilt: 2.59.2 → 2.58 (2.59+ requires AGP 9.0+)
- KSP: 2.3.20-2.3.9 → 2.3.9 (version scheme changed; no longer prefixed
  with Kotlin version)
- Gradle wrapper: 8.11.1 → 8.13 (required by AGP 8.13.2)

## Implementation Decisions

### StreamCandidate added as explicit domain model
The design docs imply this type but do not name it. StreamCandidate represents
an unresolved torrent or magnet link found during search. It becomes a
StreamSource only after the Debrid system confirms it is cached and resolves
it to a direct URL. Keeping these as separate types makes the two-step
resolution process explicit and prevents unresolved candidates from being
passed to the player.

### ProviderResult<T> introduced as shared return type
Kotlin's Result<T> wraps exceptions. Provider errors use a sealed interface,
not exceptions. A custom two-case sealed interface (Success / Failure) keeps
error handling explicit and type-safe without forcing ProviderError into a
fake exception wrapper. Lives in domain/error/.

### resolveExternalId returns ProviderResult<String?>
There is a meaningful distinction between a lookup failing (network/auth error)
and a lookup succeeding but finding no mapping. Failure covers the first case.
Success(null) covers the second. Collapsing both into NotFound would prevent
callers from knowing whether to retry.

### Provider identity uses String id plus displayName
Every provider interface declares id: String and displayName: String. id is
the machine identifier used for provider priorities in UserProfile.
displayName is the human-readable name for UI display.

### AccountInfo scoped to debrid package
AccountInfo is not a shared domain model. Different debrid providers will
have different account shapes. It lives in provider/debrid/.

### DAOs use targeted SQL updates for state changes
DownloadDao and ContinueWatchingDao use targeted UPDATE queries rather than
read-modify-write cycles. markAsFailed() increments retryCount atomically
in SQL. updateProgress() touches only downloadedBytes.

### CacheEntryDao passes now: Long rather than reading system time
All expiry-sensitive queries take a now: Long parameter for testability.
The Cache System passes System.currentTimeMillis() in production.

### @Binds used in ProviderModule instead of @Provides
Interface-to-implementation bindings use @Binds in an abstract class.
More efficient than @Provides; no runtime overhead.

## Open TODOs
- OneDebridTheme needs a static fallback colour scheme for API 26-30.
  Deferred until a palette decision is made. Lives in MainActivity.kt for
  now, moves to ui/theme/Theme.kt when the UI layer is built.
- SearchRepository.updateSearchSession uses Map<String, String> for filters;
  if SearchFilters is promoted to domain/model/, update this signature
- App icon: AndroidManifest.xml uses placeholder system drawable; real
  mipmap icons needed before any release build

## Next Steps
1. CoroutineDispatchers — inject dispatcher interface per technical
   standards; required before Use Cases can be written; small file,
   do this first
2. Repository implementations — concrete classes for each repository
   interface backed by Room DAOs and provider stubs
3. Use Cases — first business logic layer; requires CoroutineDispatchers
   to be in place first

## Key Version Numbers
- AGP: 8.13.2
- : 2.3.20
- KSP: 2.3.9
- Hilt: 2.58
- Compose BOM: 2026.04.01
- Room: 2.7.1
- Coil: 3.1.0 (group: io.coil-kt.coil3)
- Media3: 1.6.1
- Navigation Compose: 2.9.8
- DataStore: 1.2.1
- Gradle wrapper: 8.13
- Material: 1.12.0!