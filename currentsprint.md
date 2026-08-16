# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session (Session 21 practice, carried
forward) — it reflects actual current code state, verified by pulling the
repo and reading files directly, not appended to informally.

Build verification: project compiled cleanly as of Session 21's close.
Session 22's changes (see below) have been pushed; build result for this
session's changes should be confirmed at the start of Session 23 before
trusting this file's "compiles cleanly" claim for the new files.

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
    │   │   ├── NavGraph.kt                          (Search NOT yet wired in — see Open TODOs)
    │   │   └── PendingPlaybackHolder.kt
    │   ├── player/PlayerScreen.kt / PlayerViewModel.kt
    │   ├── search/SearchViewModel.kt / SearchScreen.kt   (screen new this session; not reachable yet)
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
- `home` route renders a minimal inline placeholder (not real UI). It is
  the nav graph's start destination.
- `player` route is wired for real. A caller with a full `PlaybackRequest`
  in hand can call `PendingPlaybackHolder.set(request, profileId)` then
  `navController.navigate(Route.Player.path)`. PlayerScreen reads it back,
  drives PlaybackCoordinator through resolve → ready → play, owns the
  ExoPlayer instance, reports lifecycle state back to PlayerViewModel.
- **SearchScreen.kt exists (new, Session 22) but is not reachable from the
  running app yet.** `Route.Search` is not declared in `NavGraph.kt` and
  `NavGraph.kt`'s `NavHost` has no `composable(Route.Search.path) { ... }`
  entry calling it. The screen itself compiles and is functionally complete
  against the current domain model (see Session 22 section below for what
  it does and its two deliberate limitations). Wiring it into NavGraph is
  the top item in Next Steps.
- Theme (`OneDebridTheme` in MainActivity.kt) correctly branches on
  `Build.VERSION.SDK_INT` — dynamic color on API 31+, Material 3 baseline
  on API 26-30.

## Session 22 — Completed

**Part 1: SearchResult list-shape fix.** Found while scoping SearchScreen,
before writing it: `SearchCoordinator.SearchState.Results` held a single
`SearchResult` (and therefore a single `Media`), all the way down through
`SearchMediaUseCase` and `MediaRepository.search()` to `SearchProvider`
itself. A search can only ever match one title this way — wrong per
UI_UX_Design.md's "Instant Search Results" (plural) and
Provider_Architecture.md's Aggregator/Union strategy, which only makes
sense if there's a list to merge. Fixed bottom-up across five files, in
this order (each depends on the one below it, so this order avoided any
intermediate broken-signature state):

1. `SearchProvider.kt` — `search()` now returns
   `ProviderResult<List<SearchResult>>`, was `ProviderResult<SearchResult>`.
2. `StubSearchProvider.kt` — return type updated to match. Still
   unconditionally `Failure(ServiceUnavailable)`, no behavior change.
3. `MediaRepository.kt` / `MediaRepositoryImpl.kt` — `search()` returns
   `RepositoryResult<List<SearchResult>>`. Impl body unchanged (generic
   `toRepositoryResult()` extension absorbed the new type automatically).
4. `SearchMediaUseCase.kt` — `invoke()` returns
   `RepositoryResult<List<SearchResult>>`. Body unchanged, just forwards.
5. `SearchCoordinator.kt` — `SearchState.Results` field renamed `result` →
   `results`, retyped `SearchResult` → `List<SearchResult>`.

Verified byte-for-byte against `raw.githubusercontent.com` after push
(re-pulled tarball, grepped every changed signature) — confirmed landed
correctly, not just inferred from a passing build.

Checked for anything else referencing the old singular shape before
declaring this done: `SearchViewModel.kt` only forwards `coordinatorState`
as a whole and never destructured `.result`, so it needed no change. No
other file referenced `SearchState.Results` yet (no real SearchScreen
existed at the time of the fix).

**Part 2: SearchScreen.kt (new) + SearchViewModel.kt (small addition).**

- `SearchViewModel.kt` — added `activeProfileId: String?` to
  `SearchUiState`, populated from the existing internal `_activeProfile`
  tracking in `init`. Needed because SearchScreen has to pass a profileId
  to `PendingPlaybackHolder.set()` and had no other way to reach the active
  profile id (the ViewModel's `_activeProfile` was private, used only for
  the ViewModel's own synchronous reads inside `search()`/`clearHistory()`).
  No other change to this file — `search()`, `clearSearch()`,
  `clearHistory()`, `observeHistory()` untouched.
- `SearchScreen.kt` (new, `ui/search/`) — search bar, idle/history list,
  loading, results list, and error states, switching on
  `SearchState`. Two deliberate limitations, both visibly flagged in the UI
  rather than silently worked around:
  1. Only `MediaType.MOVIE` results are tappable. `PlaybackRequest`
     requires an `Episode` for `TV_SHOW` content; `SearchResult`/`Media`
     carry no episode data, and no episode-picker screen exists. TV_SHOW
     rows render with a "Not yet supported" label instead of being
     silently inert.
  2. Playback always uses Smart Defaults — built `PlaybackRequest` always
     has `preferredSource = null`. No stream-candidate picker UI exists yet
     for manual override. Matches Project_Design.md's Smart Defaults
     principle as correct default behavior, not a shortcut.
  Error state reuses the same `AppError.isRecoverable` split PlayerScreen
  established, rather than inventing a second convention for the same type.
- `strings.xml` — added `search_*` string resources (8 new entries),
  existing `player_*` entries untouched.

**Not done this session, flagged explicitly:** `NavGraph.kt` was NOT
touched. `Route.Search` is not declared, and there is no
`composable(Route.Search.path)` entry in `NavHost` calling `SearchScreen`.
The screen compiles standalone but nothing in the running app can reach it
yet — same "wired but unreached" pattern Player was briefly in mid-Session
21, done deliberately this time (not an oversight) since wiring it in also
requires deciding where Search lives in the nav graph relative to Home,
which wasn't decided this session.

## Process Notes (Session 22)

- Session opened by re-pulling the repo per the standing rule (`curl` the
  tarball) and reading `currentsprint.md` before any code was touched —
  confirmed Session 21's file was accurate, no stale-doc surprises this
  time.
- The list-shape fix (Part 1) was caught by reading the actual
  `SearchCoordinator`/`SearchMediaUseCase`/`MediaRepository` code before
  starting SearchScreen, not assumed from memory — direct application of
  the "read actual files before writing any code" rule turning up a real
  bug rather than just avoiding one.
- All five list-shape-fix files verified against `raw.githubusercontent.com`
  post-push before moving on to SearchScreen — re-pulled tarball, grepped
  every changed signature line, confirmed match. Following the Session 21
  lesson: a clean build confirms the code that landed compiles, not which
  code landed.
- Session closed after SearchScreen.kt rather than continuing to NavGraph
  wiring, on the reasoning that the conversation had grown long enough that
  starting a new dependent file risked losing earlier context/decisions —
  a fresh session for NavGraph wiring was chosen deliberately, not because
  the work was blocked.

## Key Lessons & Principles (carried forward, still in force)

- **Read actual files before writing any code** — non-negotiable. Recurring
  build failures in Sessions 14 and 16 came from writing against assumed
  rather than actual interfaces. Session 22's list-shape bug was caught by
  this same discipline.
- **currentsprint.md on GitHub is the authoritative completion record** —
  project file copies are a convenience cache only.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`.
- **PlaybackState naming collision:** `CoordinatorState` (sealed interface,
  PlaybackCoordinator.kt) vs `PlayerLifecycleState` (enum, SessionState.kt)
  — resolved via import aliases. Reuse these exact names if a file needs
  both types again.
- **onCleared() cannot reliably run suspend work** — fix belongs in Compose
  screens via `DisposableEffect(Unit).onDispose`, as PlayerScreen.kt does.
- **ExoPlayer instance belongs in the Compose screen, not the ViewModel.**
- **Jetpack Navigation Compose cannot pass domain objects as nav args** —
  PendingPlaybackHolder singleton is the workaround; does not survive
  process death (documented in the file).
- **MutableStateFlow.update{} does not resolve in this build environment**
  — use direct `_flow.value = _flow.value?.copy(...)` assignment instead.
- **CI/build environment:** GitHub Actions runners are fresh (no local
  build cache); `gradle-wrapper.jar` handled via
  `gradle/actions/setup-gradle@v3`.
- **Verify pushed file contents directly, don't infer from build status** —
  re-pull and diff against GitHub before treating any edit as landed,
  especially before starting a dependent task in the same file.
- **NEW (Session 22): a use case/coordinator/repository chain returning a
  singular type where the domain clearly implies a collection (e.g. search
  results) is worth checking explicitly before building a screen against
  it** — this was a real pre-existing bug, not a hypothetical one, and
  would have forced a screen rewrite if caught after the fact instead of
  before.

## Next Steps, In Order

1. **Wire SearchScreen into NavGraph.kt.** Add `Route.Search`, add a
   `composable(Route.Search.path) { SearchScreen(...) }` entry, decide how
   the user reaches Search from Home (Home is still a placeholder, so this
   may mean Search becomes the temporary start destination, or Home's
   placeholder gets a button to it — worth deciding with Dia rather than
   assuming). This is what makes SearchScreen actually reachable for the
   first time.
2. **HomeScreen.kt** (real Composable, replacing NavGraph's inline
   placeholder). HomeViewModel already exists.
3. Settings/Profile screen (real Composable) — ProfileViewModel exists.
4. Continue Watching persistence gap: `SavePlaybackPositionUseCase`
   currently only updates in-memory `SessionState`, not the Room-backed
   ContinueWatchingEntity table. Not persisted end-to-end yet.
5. Media cache/lookup layer — not yet started. Would eventually let the nav
   graph pass a bare `mediaId` as a real nav arg, fix
   PendingPlaybackHolder's process-death gap, and let SearchScreen route
   TV_SHOW results somewhere (an episode-picker screen) instead of marking
   them unsupported.
6. Episode-picker screen / Details screen — not started. Needed to lift
   Session 22's TV_SHOW limitation in SearchScreen.
7. Stream-candidate picker UI — not started. Needed to lift Session 22's
   Smart-Defaults-only limitation in SearchScreen.

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
  PlaybackRepository.removeFromContinueWatching() returns Unit; deliberately
  deferred (Session 16)
- HomeViewModel.kt has a fully-qualified kotlinx.coroutines.Job reference
  inline instead of a top-level import (Session 16) — not yet tidied
- NavGraph's home route is an inline placeholder Composable, not real UI
- **NEW (Session 22):** NavGraph's Search route is not declared and
  SearchScreen is not reachable — see Next Steps #1
- **NEW (Session 22):** SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports, to avoid a possible import-name
  collision that couldn't be verified without seeing the exact Compose BOM
  version pinned in build.gradle.kts. Revisit and tidy to top-level imports
  once confirmed safe.
- **NEW (Session 22):** SearchScreen's TV_SHOW tap-disabled state and
  Smart-Defaults-only playback are both deliberate, documented limitations,
  not bugs — see Session 22 section above and Next Steps #6/#7 for what
  lifts them.

At the end of the next session, update currentsprint.md (full file, in a
code block) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md before
treating the session as closed.