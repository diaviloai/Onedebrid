# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file was stale on GitHub from Session 14 through Session 20 — it was
never fully rewritten in that window, only appended to informally. This is
a genuine full rewrite as of Session 21, reflecting actual current code
state (verified by pulling the repo and reading files directly), not a
continuation of the old partial-append pattern.

Build verification: project compiles cleanly as of Session 21
(navigation graph wired end-to-end, confirmed by Dia; theme fallback
added, confirmed by Dia).

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt
    │   │   ├── TypeConverters.kt
    │   │   ├── dao/
    │   │   │   ├── CacheEntryDao.kt
    │   │   │   ├── ContinueWatchingDao.kt
    │   │   │   ├── DownloadDao.kt
    │   │   │   ├── ProfileDao.kt
    │   │   │   ├── RecentlyPlayedDao.kt
    │   │   │   └── SearchHistoryDao.kt
    │   │   └── entity/
    │   │       ├── CacheEntryEntity.kt
    │   │       ├── ContinueWatchingEntity.kt
    │   │       ├── DownloadEntity.kt
    │   │       ├── ProfileEntity.kt
    │   │       ├── RecentlyPlayedEntity.kt
    │   │       └── SearchHistoryEntity.kt
    │   └── repository/
    │       ├── MediaRepository.kt / MediaRepositoryImpl.kt
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── SubtitleRepository.kt / SubtitleRepositoryImpl.kt
    ├── di/
    │   ├── ApplicationScope.kt / ApplicationScopeModule.kt
    │   ├── CoroutineDispatchers.kt
    │   ├── DatabaseModule.kt
    │   ├── DispatchersModule.kt
    │   ├── ProviderModule.kt
    │   └── RepositoryModule.kt
    ├── domain/
    │   ├── error/
    │   │   ├── AppError.kt
    │   │   ├── ProviderError.kt
    │   │   └── ProviderResult.kt
    │   └── model/
    │       ├── Episode.kt
    │       ├── Media.kt
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt
    │       ├── SessionState.kt
    │       ├── StreamSource.kt
    │       ├── SubtitleTrack.kt
    │       ├── UserProfile.kt
    │       └── WatchedItem.kt
    ├── provider/
    │   ├── debrid/DebridProvider.kt / StubDebridProvider.kt
    │   ├── metadata/MetadataProvider.kt / StubMetadataProvider.kt
    │   ├── search/SearchProvider.kt / StubSearchProvider.kt
    │   └── subtitle/SubtitleProvider.kt / StubSubtitleProvider.kt
    ├── ui/
    │   ├── home/HomeViewModel.kt                    (no Composable screen yet)
    │   ├── navigation/
    │   │   ├── NavGraph.kt
    │   │   └── PendingPlaybackHolder.kt
    │   ├── player/PlayerScreen.kt / PlayerViewModel.kt
    │   ├── search/SearchViewModel.kt                (no Composable screen yet)
    │   └── settings/ProfileViewModel.kt             (no Composable screen yet)
    └── usecase/
        ├── ClearPlaybackHistoryUseCase.kt
        ├── ClearSearchHistoryUseCase.kt
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetProfilesUseCase.kt
        ├── GetSearchHistoryUseCase.kt
        ├── MarkAsCompletedUseCase.kt
        ├── RecordPlaybackUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── ResolvePlaybackUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── StartPlaybackUseCase.kt
        ├── SwitchProfileUseCase.kt
        └── UpdateProfileUseCase.kt

Single `:app` module, per Project_Design.md — no multi-module split yet.

## What Actually Works End-to-End Right Now

- App launches into `NavGraph` (wired in MainActivity.kt, Session 21).
- `home` route renders a minimal inline placeholder (not real UI — see
  Open TODOs). It is the nav graph's start destination.
- `player` route is wired for real. A caller with a full `PlaybackRequest`
  (Media + optional Episode) in hand can call
  `PendingPlaybackHolder.set(request, profileId)` then
  `navController.navigate(Route.Player.path)`. PlayerScreen reads it back
  via `consume()`, drives PlaybackCoordinator through resolve → ready →
  play, owns the ExoPlayer instance, and reports lifecycle state back to
  PlayerViewModel. Error UI splits on `AppError.isRecoverable` per
  UI_UX_Design.md's severity tiers.
- No screen currently calls `PendingPlaybackHolder.set()` and navigates to
  `player` — Home/Search/Settings screens don't exist yet, so there is no
  real caller wired up yet. The player route is reachable and functional,
  but nothing in the running app currently triggers it.
- Theme (`OneDebridTheme` in MainActivity.kt) correctly branches on
  `Build.VERSION.SDK_INT` — dynamic color on API 31+, Material 3 baseline
  `lightColorScheme()`/`darkColorScheme()` on API 26-30. minSdk is 26, so
  this fallback covers real supported devices, not a theoretical range.

## Session 21 — Completed

- **PendingPlaybackHolder.kt** (new, `ui/navigation/`) — singleton,
  read-and-clear holder carrying a `PlaybackRequest` + `profileId` across
  the nav boundary, since Navigation Compose route args can't carry full
  domain objects and no Media cache/lookup layer exists yet to resolve a
  bare `mediaId` back into a full `Media`. Known limitation, documented in
  the file itself: does not survive process death (plain in-memory
  singleton, no SavedStateHandle backing).
- **PlayerScreen.kt** (edited) — signature changed from plain
  `request`/`profileId` parameters to `pendingPlaybackHolder`/
  `onMissingRequest`. Reads the pending request once via
  `remember { pendingPlaybackHolder.consume() }`; if null (e.g. reached via
  restored back stack after process death), invokes `onMissingRequest()`
  instead of proceeding.
- **NavGraph.kt** (new, `ui/navigation/`) — sealed `Route` (Home, Player
  only — Search/Settings routes deliberately not declared, since no
  Composable exists to route to yet). `NavHost` with Home as start
  destination (placeholder) and Player wired for real, including
  `onMissingRequest` routing back to Home with
  `popUpTo(Route.Home.path) { inclusive = true }`.
- **MainActivity.kt** (edited) — `NavGraph(pendingPlaybackHolder = ...)`
  now actually called in `setContent`, replacing the old placeholder
  comment; `pendingPlaybackHolder` field-injected via Hilt
  (`@Inject lateinit var`). Also carries the theme fallback fix (see
  below) — both landed in the same file edit since the first attempt at
  the nav-graph edit didn't actually push (see Process Notes).
- **OneDebridTheme fallback gap closed** — `colorScheme` selection now
  checks `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` before calling
  `dynamicDarkColorScheme`/`dynamicLightColorScheme` (API 31+ only).
  Below that, falls back to Material 3's built-in baseline palette via
  argument-less `lightColorScheme()`/`darkColorScheme()`. No custom brand
  colors defined yet, so the M3 default palette is used as-is.
- **Media.id / Episode.id consistency check — resolved, no code change
  needed.** Checked domain models, Room entities, repository mapping
  functions, use cases, and PlaybackCoordinator. `Media.id` and
  `Episode.id`/`episodeId` are `String` consistently at every layer
  checked. The only `Long` ids anywhere in the chain are
  `ContinueWatchingEntity.id`/`RecentlyPlayedEntity.id`, which are Room's
  own autogenerated row keys — unrelated to media identity, not a
  collision. Provider layer (StubSearchProvider, StubMetadataProvider) has
  nothing to check yet since both are unimplemented placeholders that
  don't mint any ids.

## Process Notes (Session 21)

- The first attempt at editing MainActivity.kt for nav-graph wiring did
  not actually get pushed to GitHub — Dia confirmed a "clean build" after
  what was actually still the pre-edit placeholder file (the build passed
  because nothing referenced NavGraph/PendingPlaybackHolder yet, so the
  omission didn't fail compilation). Caught only when re-pulling the repo
  directly before starting the theme-fallback task and diffing against
  what was actually given. Re-applied as a combined edit (nav wiring +
  theme fallback together) in one file.
- Lesson reinforced: "build successful" confirms compilation, not that the
  intended file changes are the ones that compiled. Re-pull and verify
  file contents directly against GitHub (raw.githubusercontent.com, not
  just the tarball, since the tarball checked out equivalently) before
  treating any edit as landed, especially before starting a dependent
  task in the same file.

## Key Lessons & Principles (carried forward, still in force)

- **Read actual files before writing any code** — non-negotiable. Recurring
  build failures in Sessions 14 and 16 came from writing against assumed
  rather than actual interfaces.
- **currentsprint.md on GitHub is the authoritative completion record** —
  project file copies are a convenience cache only.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`.
- **PlaybackState naming collision:** `CoordinatorState` (sealed interface,
  PlaybackCoordinator.kt) vs `PlayerLifecycleState` (enum, SessionState.kt)
  — resolved via import aliases. Reuse these exact names if a file needs
  both types again.
- **onCleared() cannot reliably run suspend work** — viewModelScope is
  cancelled before it runs. Fix belongs in Compose screens via
  `DisposableEffect(Unit).onDispose` calling `viewModel.stop()` — this is
  what PlayerScreen.kt actually does.
- **ExoPlayer instance belongs in the Compose screen, not the ViewModel**
  — UI layer boundary rule (Technical_standards.md).
- **Jetpack Navigation Compose cannot pass domain objects as nav args** —
  only primitives/strings. PendingPlaybackHolder singleton is the chosen
  workaround; known to not survive process death (documented in the file).
- **MutableStateFlow.update{} does not resolve in this build environment**
  — use direct `_flow.value = _flow.value?.copy(...)` assignment instead.
- **CI/build environment:** GitHub Actions runners are fresh (no local
  build cache); `gradle-wrapper.jar` handled via
  `gradle/actions/setup-gradle@v3`.
- **Verify pushed file contents directly, don't infer from build status**
  — see Process Notes above. A successful build means the code that
  landed compiles; it does not confirm which code landed.

## Next Steps, In Order

1. **HomeScreen.kt** (real Composable, replacing the inline placeholder in
   NavGraph.kt's `home` route). HomeViewModel already exists. This is also
   the natural place to give the nav graph a real caller into the `player`
   route (HomeViewModel currently only holds `WatchedItem` for Continue
   Watching, which carries `mediaId` but not a full `Media` — Search is
   more likely to be the first real screen with a full `Media` object in
   hand at the moment the user taps play; worth deciding which screen
   should be the first real PendingPlaybackHolder.set() caller before
   building either).
2. **SearchScreen.kt** (real Composable) — SearchViewModel exists,
   SearchCoordinator exists. Likely the more natural first caller into
   PendingPlaybackHolder, since SearchState holds full SearchResult/Media
   objects from an actual search.
3. Settings/Profile screen (real Composable) — ProfileViewModel exists.
4. Continue Watching persistence gap: `SavePlaybackPositionUseCase`
   currently only updates in-memory `SessionState`, not the Room-backed
   ContinueWatchingEntity table (flagged in that file's own docstring).
   Continue Watching is not actually persisted end-to-end yet — worth
   deciding whether this blocks HomeScreen or can be built around
   temporarily.
5. Media cache/lookup layer — not yet started. Would eventually let the
   nav graph pass a bare `mediaId` as a real nav arg and have PlayerScreen
   resolve the full Media itself, which would also fix
   PendingPlaybackHolder's process-death gap. Deliberately deferred this
   session (see PendingPlaybackHolder.kt doc comment) rather than built
   partially against the unimplemented StubMetadataProvider.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException) for
  blank name validation — semantically incorrect; revisit when the error
  model gets a review pass
- StartPlaybackUseCase uses a fully qualified AppError reference inline;
  tidy to a top-level import if preferred
- HomeViewModel.removeItem() has no failure feedback path —
  PlaybackRepository.removeFromContinueWatching() returns Unit; changing
  this requires touching the interface and PlaybackRepositoryImpl
  together, deliberately deferred (Session 16)
- HomeViewModel.kt has a fully-qualified kotlinx.coroutines.Job reference
  inline instead of a top-level import (Session 16) — not yet tidied
- **NEW (Session 21):** NavGraph's `home` route is an inline placeholder
  Composable, not real UI — see Next Steps #1
- **NEW (Session 21):** No screen currently calls
  `PendingPlaybackHolder.set()` — the player route is wired but nothing
  in the running app reaches it yet, since Home/Search have no real
  screens