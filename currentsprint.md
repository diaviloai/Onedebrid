# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 22's close,
confirmed via GitHub Actions CI after the material-icons-core fix below
(see "Session 22 — Build Failure & Fix"). This confirmation was NOT
present at the moment SearchScreen.kt was first pushed — see that section
for what actually happened and why it matters for Session 23.

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
- **SearchScreen.kt exists (new, Session 22), compiles cleanly (confirmed
  via CI after the icons-dependency fix), but is not reachable from the
  running app yet.** `Route.Search` is not declared in `NavGraph.kt` and
  `NavGraph.kt`'s `NavHost` has no `composable(Route.Search.path) { ... }`
  entry calling it. Wiring it in is the top item in Next Steps.
- Theme (`OneDebridTheme` in MainActivity.kt) correctly branches on
  `Build.VERSION.SDK_INT` — dynamic color on API 31+, Material 3 baseline
  on API 26-30.

## Session 22 — Completed

**Part 1: SearchResult list-shape fix.** Found while scoping SearchScreen,
before writing it: `SearchCoordinator.SearchState.Results` held a single
`SearchResult` (and therefore a single `Media`), all the way down through
`SearchMediaUseCase` and `MediaRepository.search()` to `SearchProvider`
itself. A search could only ever match one title this way — wrong per
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
(re-pulled tarball, grepped every changed signature) before moving on —
confirmed landed correctly, not just inferred from a passing build.

**Part 2: SearchScreen.kt (new) + SearchViewModel.kt (small addition).**

- `SearchViewModel.kt` — added `activeProfileId: String?` to
  `SearchUiState`, populated from the existing internal `_activeProfile`
  tracking in `init`. Needed because SearchScreen has to pass a profileId
  to `PendingPlaybackHolder.set()` and had no other way to reach the active
  profile id. No other change to this file.
- `SearchScreen.kt` (new, `ui/search/`) — search bar, idle/history list,
  loading, results list, and error states, switching on `SearchState`. Two
  deliberate limitations, both visibly flagged in the UI rather than
  silently worked around:
  1. Only `MediaType.MOVIE` results are tappable. `PlaybackRequest`
     requires an `Episode` for `TV_SHOW` content; `SearchResult`/`Media`
     carry no episode data, and no episode-picker screen exists. TV_SHOW
     rows render with a "Not yet supported" label instead of being
     silently inert.
  2. Playback always uses Smart Defaults — built `PlaybackRequest` always
     has `preferredSource = null`. No stream-candidate picker UI exists yet
     for manual override.
  Error state reuses the same `AppError.isRecoverable` split PlayerScreen
  established, rather than inventing a second convention for the same type.
- `strings.xml` — added `search_*` string resources (8 new entries),
  existing `player_*` entries untouched.

**Not done this session:** `NavGraph.kt` was NOT touched. `Route.Search` is
not declared, no `composable(Route.Search.path)` entry exists. SearchScreen
compiles standalone but nothing in the running app can reach it yet —
deliberate, since wiring it in also requires deciding where Search lives
in the nav graph relative to Home.

## Session 22 — Build Failure & Fix (important — read before Session 23)

The first push of `SearchScreen.kt` **failed CI**, contradicting the
initial close-out message given alongside that file, which described the
session as done without having seen a build result. That was a process
mistake — treating the file as complete based on "it should compile"
reasoning rather than an actual green build, exactly the "build successful
does not confirm what's claimed" trap the project's own lessons already
warn about, just from the opposite direction (this time no CI check had
even run yet, rather than CI passing on the wrong content).

**Root cause:** `SearchScreen.kt` used `Icons.Filled.Search` (from
`androidx.compose.material.icons.Icons`) without first checking whether
that artifact was declared as a dependency. It wasn't — grepping
`app/build.gradle.kts` after the failure showed no prior file in the
codebase had ever used `Icons`, so nothing had pulled in
`material-icons-core` before now. This is the same "read the actual file
before writing code" lesson the project already tracks, just applied to a
build file instead of a Kotlin source file — the fix going forward is to
grep `build.gradle.kts` for a dependency before importing from it, not
just assume common Compose/Material APIs are already available.

**Fix, two files:**
1. `gradle/libs.versions.toml` — added
   `androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }`
   under the Compose libraries block. No `version.ref` — rides the existing
   `composeBom` version like the other Compose artifacts already do (see
   the file's own comment on why: BOM manages Compose artifact versions).
2. `app/build.gradle.kts` — added
   `implementation(libs.androidx.compose.material.icons.core)` directly
   after the existing `implementation(libs.androidx.compose.material3)`
   line in the `// Compose` dependency block.

`material-icons-core` was chosen over `material-icons-extended` — core
contains `Icons.Filled.Search` and is the leaner, correctly-scoped choice
per Technical_Standards.md's dependency discipline; extended is
substantially larger and unjustified for one icon.

**Verified:** re-pulled the repo fresh after the fix was pushed, grepped
`libs.versions.toml`, `app/build.gradle.kts`, and `SearchScreen.kt`
directly to confirm all three pieces were live together, then confirmed
green via GitHub Actions CI. This build result is the first real
confirmation SearchScreen.kt compiles — treat everything above this
section as verified, but treat the original "session closed cleanly"
framing given before this fix as inaccurate; this section supersedes it.

## Process Notes (Session 22)

- Session opened by re-pulling the repo per the standing rule and reading
  `currentsprint.md` before any code was touched — confirmed Session 21's
  file was accurate.
- The list-shape fix (Part 1) was caught by reading the actual
  `SearchCoordinator`/`SearchMediaUseCase`/`MediaRepository` code before
  starting SearchScreen, not assumed from memory.
- All five list-shape-fix files verified against `raw.githubusercontent.com`
  post-push before moving on to SearchScreen.
- **SearchScreen.kt was NOT similarly protected before its first push** —
  it was declared complete without a build check, and without grepping
  `build.gradle.kts` for the `Icons` dependency it used. This gap is why
  the CI failure happened and is the specific mistake to avoid repeating.
- Session closed (this time, after the fix) only once CI confirmed green
  and the fix was verified live against the actual repo content — not
  before.

## Key Lessons & Principles (carried forward, still in force)

- **Read actual files before writing any code** — non-negotiable. Sessions
  14 and 16 had build failures from writing against assumed interfaces.
  Session 22 applied this to catch the SearchResult list-shape bug
  correctly, but then failed to apply the same discipline to a Gradle
  dependency, causing the icons build failure. The lesson now explicitly
  covers build files, not just Kotlin interfaces — see next bullet.
- **NEW (Session 22): grep build.gradle.kts / libs.versions.toml before
  importing anything from a library that hasn't been used elsewhere in the
  codebase yet** — even common-seeming APIs (like Material `Icons`) may
  not have their artifact declared. Don't assume a dependency exists just
  because the API is well-known; check the actual declared dependencies
  first.
- **NEW (Session 22): don't declare a session or a file "done" without an
  actual CI result.** Reasoning that code "should" compile is not the same
  as knowing it does — this is the same principle as "build successful
  doesn't confirm the intended code compiled," applied to the case where
  no build has run at all yet, not just the case where it passed on the
  wrong content.
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
  re-pull and diff against GitHub before treating any edit as landed.
- **A use case/coordinator/repository chain returning a singular type where
  the domain clearly implies a collection is worth checking explicitly
  before building a screen against it** (Session 22 SearchResult example).

## Next Steps, In Order

1. **Wire SearchScreen into NavGraph.kt.** Add `Route.Search`, add a
   `composable(Route.Search.path) { SearchScreen(...) }` entry, decide how
   the user reaches Search from Home (Home is still a placeholder, so this
   may mean Search becomes the temporary start destination, or Home's
   placeholder gets a button to it — decide with Dia rather than assuming).
2. **HomeScreen.kt** (real Composable, replacing NavGraph's inline
   placeholder). HomeViewModel already exists.
3. Settings/Profile screen (real Composable) — ProfileViewModel exists.
4. Continue Watching persistence gap: `SavePlaybackPositionUseCase`
   currently only updates in-memory `SessionState`, not the Room-backed
   ContinueWatchingEntity table.
5. Media cache/lookup layer — not yet started. Would eventually let the nav
   graph pass a bare `mediaId` as a real nav arg, fix
   PendingPlaybackHolder's process-death gap, and let SearchScreen route
   TV_SHOW results to an episode-picker screen instead of marking them
   unsupported.
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
- NavGraph's Search route is not declared and SearchScreen is not
  reachable — see Next Steps #1
- SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports, to avoid a possible import-name
  collision that couldn't be verified without seeing the exact Compose BOM
  version pinned in build.gradle.kts. Revisit and tidy to top-level imports
  once confirmed safe.
- SearchScreen's TV_SHOW tap-disabled state and Smart-Defaults-only
  playback are both deliberate, documented limitations, not bugs.

At the end of the next session, update currentsprint.md (full file, in a
code block) and verify it directly against
raw.githubusercontent.com/diaviloai/Onedebrid/main/currentsprint.md before
treating the session as closed — and do not treat any session as closed
without an actual green CI result for whatever was last pushed.