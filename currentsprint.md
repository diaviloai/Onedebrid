# OneDebrid — Current Sprint

## Status

Implementation in progress. Architectural design phase complete.

This file is fully rewritten each session — it reflects actual current
code state, verified by pulling the repo and reading files directly, not
appended to informally.

Build verification: project compiles cleanly as of Session 27's close,
confirmed via GitHub Actions on the latest pushed commit — job "build"
succeeded, per the direct run/job URL Dia provided
(`github.com/diaviloai/Onedebrid/actions/runs/32750760290/job/97506903198`).
This was a **retry** of an earlier run this session
(`runs/32691516649/job/97325924983`) that failed for two different
reasons in sequence — see "Session 27 — What Was Done" below for the
full account, including a real compile-breaking mistake made and fixed
within this session. All files touched this session were independently
re-pulled from `raw.githubusercontent.com` after the fix and structurally
verified (brace-balance checked, tails inspected) before this file was
updated.

**Sessions 1–25 summary** (condensed from prior full write-ups, which
remain in git history on this file if the detail is ever needed): built
layer by layer — domain models → error types → provider interfaces →
repository interfaces → Room entities/DAOs → Hilt wiring → coroutine
infrastructure → use cases → coordinators → ViewModels → Compose screens
(SearchScreen, HomeScreen, SettingsScreen). Session 25 added Continue
Watching tap-to-resume (`PendingPlaybackHolder` + direct-to-Player nav)
and cache-first `MediaRepository` reads via `MediaCache`.

**Session 26 summary:** built the Details/Episode-picker screen
(`ui/details/DetailsScreen.kt` + `DetailsViewModel.kt`), reached only from
Search. `Route.Details` became the first route in the graph to carry a
`mediaId` nav arg. Search's results became fully tappable (closing the
"TV_SHOW not yet supported" gap). Continue Watching's direct-to-Player
flow was deliberately left unchanged (explicit scope decision, to
preserve exact `resumePositionMs` behavior). Also fixed a real
pre-existing bug: `Media.kt` was missing `@Serializable` on the `Media`
class itself despite `MediaCache.kt` requiring it for JSON
encode/decode — found on a fresh read at the start of that session
(despite Session 25's handoff claiming it was already fixed), corrected,
verified independently green.

## Package Structure

com.onedebrid.app/
    ├── MainActivity.kt (Session 27: no longer field-injects
    │   PendingPlaybackHolder — that class deleted this session)
    ├── OneDebridApplication.kt
    ├── coordinator/
    │   ├── PlaybackCoordinator.kt
    │   ├── SearchCoordinator.kt
    │   └── SessionCoordinator.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppDatabase.kt
    │   │   ├── MediaCache.kt (Session 25 — wraps CacheEntryDao for
    │   │   │   Media/Episode-list JSON caching; doc-comment-only
    │   │   │   PendingPlaybackHolder reference still pending cleanup,
    │   │   │   see Open TODOs)
    │   │   ├── TypeConverters.kt
    │   │   ├── dao/ (unchanged)
    │   │   └── entity/ (unchanged)
    │   └── repository/
    │       ├── MediaRepository.kt (Session 27: added
    │       │   getEpisodeById(mediaId, episodeId) to the interface)
    │       ├── MediaRepositoryImpl.kt (Session 27: implements
    │       │   getEpisodeById() via getEpisodes() + in-memory filter —
    │       │   see "What Was Done" below for a build-breaking mistake
    │       │   made and fixed while adding this)
    │       ├── PlaybackRepository.kt / PlaybackRepositoryImpl.kt
    │       ├── ProfileRepository.kt / ProfileRepositoryImpl.kt
    │       ├── RepositoryResult.kt
    │       ├── SearchRepository.kt / SearchRepositoryImpl.kt
    │       ├── SessionRepository.kt / SessionRepositoryImpl.kt
    │       └── (Subtitle/Download repositories not yet built)
    ├── di/
    │   ├── CoroutineDispatchers.kt
    │   └── (Hilt modules — DatabaseModule, RepositoryModule; neither
    │       needed changes this session)
    ├── domain/
    │   ├── error/
    │   │   └── AppError.kt (Unknown case now also covers
    │   │       getEpisodeById()'s not-found case, Session 27 — see Open
    │   │       TODOs re: future ValidationError-style review)
    │   └── model/
    │       ├── Media.kt
    │       ├── Episode.kt
    │       ├── PlaybackRequest.kt
    │       ├── SearchResult.kt
    │       ├── SessionState.kt (SessionState, PlaybackSession,
    │       │   SearchSession, PlaybackState enum)
    │       ├── StreamSource.kt (VideoQuality enum)
    │       ├── SubtitleTrack.kt (SubtitleFormat enum)
    │       ├── UserProfile.kt (PlaybackPreferences, SubtitlePreferences,
    │       │   SearchPreferences, ThemePreferences)
    │       └── WatchedItem.kt
    ├── provider/
    │   └── (SearchProvider, StubSearchProvider, MetadataProvider,
    │       StubMetadataProvider, DebridProvider, others — unchanged;
    │       no per-episode-id provider call exists, see Session 27 notes)
    ├── ui/
    │   ├── details/
    │   │   ├── DetailsScreen.kt (Session 27: onNavigateToPlayer
    │   │   │   signature changed to carry mediaId/episodeId/resumeMs)
    │   │   └── DetailsViewModel.kt (Session 27: onPlayMovie/onPlayEpisode
    │   │       simplified — emit PlayerNavArgs instead of building a
    │   │       PlaybackRequest; no longer needs GetActiveProfileUseCase;
    │   │       had a real missing-closing-brace bug this session, fixed
    │   │       — see "What Was Done")
    │   ├── home/
    │   │   ├── HomeScreen.kt (Session 27: onNavigateToPlayer signature
    │   │   │   changed; removed the now-dead resolvingMediaId/isResolving/
    │   │   │   resumeError UI — see "What Was Done")
    │   │   └── HomeViewModel.kt (Session 27: onItemClick() simplified to
    │   │       emit PlayerNavArgs immediately, no longer resolves Media
    │   │       first; no longer needs GetMediaByIdUseCase)
    │   ├── navigation/
    │   │   ├── NavGraph.kt (Session 27: Route.Player now carries mediaId/
    │   │   │   episodeId/resumeMs nav args instead of taking a
    │   │   │   PendingPlaybackHolder parameter)
    │   │   └── PlayerNavArgs.kt (Session 27 — new; shared nav-arg payload
    │   │       type used by both HomeViewModel and DetailsViewModel)
    │   │   (PendingPlaybackHolder.kt deleted this session)
    │   ├── player/
    │   │   ├── PlayerScreen.kt (Session 27: no longer takes
    │   │   │   pendingPlaybackHolder/onMissingRequest params; renders a
    │   │   │   new ResolveState layer above the existing CoordinatorState
    │   │   │   handling)
    │   │   └── PlayerViewModel.kt (Session 27: takes SavedStateHandle
    │   │       instead of receiving a ready PlaybackRequest; resolves its
    │   │       own Media/Episode/active-profile on init — see "What Was
    │   │       Done" for full reasoning)
    │   ├── search/
    │   │   ├── SearchScreen.kt (doc-comment-only PendingPlaybackHolder
    │   │       reference still pending cleanup, see Open TODOs)
    │   │   └── SearchViewModel.kt (same — doc-comment-only, pending)
    │   └── settings/
    │       ├── SettingsScreen.kt
    │       └── ProfileViewModel.kt
    └── usecase/
        ├── CreateProfileUseCase.kt
        ├── DeleteProfileUseCase.kt
        ├── EndPlaybackSessionUseCase.kt
        ├── GetActiveProfileUseCase.kt
        ├── GetContinueWatchingUseCase.kt
        ├── GetEpisodeByIdUseCase.kt (Session 27 — new, thin wrapper
        │   around MediaRepository.getEpisodeById(), same shape as
        │   GetMediaByIdUseCase)
        ├── GetEpisodesUseCase.kt
        ├── GetMediaByIdUseCase.kt
        ├── RemoveFromContinueWatchingUseCase.kt
        ├── SavePlaybackPositionUseCase.kt
        ├── SearchMediaUseCase.kt
        ├── SwitchProfileUseCase.kt
        ├── UpdateProfileUseCase.kt
        └── (others per earlier sessions)

(This tree reflects what's been directly read/touched across sessions,
not a guaranteed exhaustive listing — see the repo itself for ground
truth on files not mentioned in recent session notes.)

## App Navigation State (as of Session 27)

Five routes exist, all wired into a single `NavGraph.kt`:

- **Home** (`Route.Home`, start destination) → real `HomeScreen.kt`.
  Shows Continue Watching. Rows are tappable to resume playback. As of
  Session 27, tapping a row navigates **immediately** to Player via
  `Route.Player.build(mediaId, episodeId, resumeMs)` — no more resolving
  a `Media` on Home first. Any resolution failure now surfaces on the
  Player screen itself via its existing error card + retry, not inline on
  the Home row. This was an explicit, discussed tradeoff (see "What Was
  Done" below), not an oversight. Top bar has Search and Settings
  actions.
- **Search** (`Route.Search`) → real `SearchScreen.kt`. Reached via
  Home's Search button. Tapping any result (movie or TV show) navigates
  to Details with that result's `mediaId`.
- **Details** (`Route.Details`) → real `DetailsScreen.kt`. Takes a
  `mediaId` nav argument. Reached only from Search. Re-fetches the full
  `Media` via `GetMediaByIdUseCase`; for `MediaType.TV_SHOW`, also
  fetches the episode list via `GetEpisodesUseCase`. Movie: header +
  single Play action. TV show: header + episode list grouped by season.
  As of Session 27, both play actions navigate directly to Player via
  `Route.Player.build(mediaId, episodeId, resumeMs = null)` — no more
  `PendingPlaybackHolder` handoff. Back-press returns to Search.
- **Player** (`Route.Player`) → real `PlayerScreen.kt`. As of Session 27,
  takes `mediaId` (required path segment), `episodeId` (optional,
  sentinel `"none"`), and `resumeMs` (optional, sentinel `-1L`) as real
  nav args — see `Route.Player`'s own doc comment in `NavGraph.kt` for
  why sentinels rather than nullable NavTypes. `PlayerViewModel` resolves
  `Media`/`Episode`/active profile itself from these on init, then builds
  its own `PlaybackRequest` and calls `PlaybackCoordinator.play()`.
  `preferredSource` is always `null` (no stream-candidate picker exists
  yet — unchanged gap, not a regression). There is no more
  "nothing pending" case: a `mediaId` is always present, so there is
  always something to attempt to resolve, even if that resolution can
  itself fail (shown via `PlayerScreen`'s error card).
  `PendingPlaybackHolder` no longer exists in this codebase.
- **Settings** (`Route.Settings`) → real `SettingsScreen.kt`. Reached
  via Home's Settings button. Leaf destination.

No `Route` entries exist beyond these five.
## Session 27 — What Was Done

**Scope agreed with Dia up front:** replace `PendingPlaybackHolder` with
real nav arguments to Player (Next Steps #1 from Session 26). Confirmed
this was the priority over the other two candidates (stream-candidate
picker UI; Continue Watching → Details routing) before starting, per
standing practice.

**Design discussion before code, as flagged in Session 26's handoff:**
- `media`/`episode` are derivable from `mediaId`/`episodeId` nav args via
  existing/new use cases. `resumePositionMs` has no such derivation path
  (it only ever existed because the calling screen had it in memory at
  tap time) and `preferredSource` doesn't exist anywhere yet (no picker
  UI). Decision: full nav args (`mediaId`, `episodeId`, `resumeMs`),
  `preferredSource` stays `null` — same as every existing caller before
  this session, not a new gap.
- No single-episode lookup existed anywhere in the codebase (only
  `getEpisodes()`, full list per show) — `MetadataProvider.fetchEpisodes`
  and `MediaCache`'s episode cache are both list-granularity only, and a
  real per-episode API call would be speculative (TMDB-style APIs fetch
  per-season, not per-episode-id). Decision, after discussing tradeoffs
  of three options (fetch-and-filter with no new code; new use case +
  repository method; pass season/episode numbers as nav args and build a
  minimal Episode): add `MediaRepository.getEpisodeById()`, implemented
  via `getEpisodes()` + an in-memory filter. This isolates the "how"
  behind a stable interface — when real per-episode provider/cache
  granularity exists later, only `MediaRepositoryImpl` needs to change.
- Confirmed explicitly with Dia: moving Home's Continue Watching failure
  surface from an inline row state to Player's existing error card
  (`AllProvidersUnavailable` is `isRecoverable = true`, so it already gets
  a Retry button there) was acceptable, given Player's error presentation
  is equally actionable — just one screen later, with a back-press needed
  to return to Home on failure instead of staying put.
- Decided to move `PlayerNavArgs` (originally added to `ui.home`) into
  `ui.navigation` alongside `Route`, since it's a nav-layer concept shared
  by two ViewModels' packages, not something that belongs to Home
  specifically.
- Confirmed with Dia to fully clear the file list this session (including
  `MainActivity.kt`, `HomeScreen.kt`, `DetailsScreen.kt`, and deleting
  `PendingPlaybackHolder.kt` itself — all surfaced mid-session as the true
  scope became clear from reading actual call sites, not assumed
  upfront). Comment-only cleanups in `SearchViewModel.kt`/
  `SearchScreen.kt`/`MediaCache.kt` were explicitly punted to next
  session due to time — see Open TODOs.

**Files created:**
- **`usecase/GetEpisodeByIdUseCase.kt`** — thin wrapper around
  `MediaRepository.getEpisodeById()`, same shape as `GetMediaByIdUseCase`.
- **`ui/navigation/PlayerNavArgs.kt`** — `data class PlayerNavArgs(mediaId,
  episodeId, resumeMs)`, shared by `HomeViewModel` and `DetailsViewModel`'s
  navigation events.

**Files modified:**
- **`data/repository/MediaRepository.kt`** — added `getEpisodeById()` to
  the interface, documented as internally implemented via `getEpisodes()`
  today, subject to change.
- **`data/repository/MediaRepositoryImpl.kt`** — implemented
  `getEpisodeById()`. **Real mistake made and fixed this session:** the
  first paste inserted the new method after the class's actual closing
  brace instead of before it (a `str_replace` anchor match landed in the
  wrong place relative to where the class body actually ended), which
  duplicated and truncated the existing `resolveStream()` method at the
  literal end of the file. This produced the first CI failure — a full
  cascade of "not abstract," "override not applicable to top level
  function," "unresolved reference" errors, all stemming from the same
  root cause. **Confirmed the original `resolveStream()` body was NOT
  actually lost** — it was still intact, complete, earlier in the file
  (this was checked via git commit history, which showed only one commit
  ever touching this file, then via direct comparison of the two
  `resolveStream` occurrences in the live pulled file) — before writing
  the fix, rather than reconstructing the method from memory. Fixed via a
  full-file overwrite with the duplicate/truncated tail removed and
  `getEpisodeById()` correctly placed inside the class body. Verified via
  brace-balance count after the fix.
- **`ui/navigation/NavGraph.kt`** — `Route.Player` now takes `mediaId`
  (required), `episodeId` (optional, `"none"` sentinel), `resumeMs`
  (optional, `-1L` sentinel) nav args with a `build()` helper.
  `HomeScreen`/`DetailsScreen` composable call sites updated to pass
  `onNavigateToPlayer` as a 3-arg lambda instead of `() -> Unit`.
- **`MainActivity.kt`** — no longer field-injects `PendingPlaybackHolder`
  or passes it to `NavGraph()`.
- **`ui/player/PlayerViewModel.kt`** — full rewrite. Takes
  `SavedStateHandle` + `GetMediaByIdUseCase` + `GetEpisodeByIdUseCase` +
  `GetActiveProfileUseCase` (new dependencies) alongside the existing
  `PlaybackCoordinator`/`SavePlaybackPositionUseCase`/
  `EndPlaybackSessionUseCase`. New `resolveAndPlay()` runs once on init:
  resolves the active profile (`.first()` on the Flow, not an ongoing
  subscription — this ViewModel only needs it once), then `Media`, then
  `Episode` if an `episodeId` was passed, builds a `PlaybackRequest`
  (`preferredSource = null`, unchanged gap), and calls
  `playbackCoordinator.play()`. New `ResolveState` sealed interface
  (Resolving/Resolved/Error) tracks this phase, exposed on `PlayerUiState`
  alongside the pre-existing `CoordinatorState`. Two distinct retry
  paths: `retryResolve()` (re-runs the whole resolve-and-play flow, for a
  `ResolveState.Error`) and `retryPlay()` (re-plays with already-resolved
  data, for a `CoordinatorState.Error`) — kept as separate small methods
  rather than a shared helper, a deliberate judgment call to avoid
  over-abstracting two ~15-line blocks.
- **`ui/player/PlayerScreen.kt`** — full rewrite. No longer takes
  `pendingPlaybackHolder`/`onMissingRequest` params. Renders
  `uiState.resolveState` first (Resolving/Error/Resolved), and only once
  `Resolved` does it look at `coordinatorState` the same way it always
  did. `ErrorContent` is now shared between both error sources (both wrap
  an `AppError` and fit the same `isRecoverable` tiers from
  `UI_UX_Design.md`).
- **`ui/home/HomeViewModel.kt`** — full rewrite. `onItemClick()` no
  longer resolves `Media` — reads `mediaId`/`episodeId`/`positionMs`
  directly off the tapped `WatchedItem` and emits a `PlayerNavArgs`
  navigation event immediately. No longer depends on
  `GetMediaByIdUseCase`. `HomeUiState.resolvingMediaId`/`resumeError`
  removed (dead state — see Known Limitations for the tradeoff this
  represents).
- **`ui/home/HomeScreen.kt`** — full rewrite. `onNavigateToPlayer` now
  takes `(mediaId, episodeId, resumeMs)`. Removed the now-dead
  `isResolving` spinner and inline `resumeError` text on
  `ContinueWatchingRow` (nothing produces that state anymore).
  `R.string.home_resolving_media`/`home_resume_error` are now unused,
  left in place — see Open TODOs, same "flag don't silently orphan"
  handling as `search_tv_show_unsupported`.
- **`ui/details/DetailsScreen.kt`** — `onNavigateToPlayer` signature
  changed to `(mediaId, episodeId, resumeMs) -> Unit`; `LaunchedEffect`
  body updated to pass all three through from the collected
  `PlayerNavArgs`.
- **`ui/details/DetailsViewModel.kt`** — `onPlayMovie()`/`onPlayEpisode()`
  simplified to emit `PlayerNavArgs` directly instead of building a
  `PlaybackRequest` and populating `PendingPlaybackHolder`. No longer
  depends on `GetActiveProfileUseCase` (Player resolves the active
  profile itself now). **Real mistake made and fixed this session:** the
  live file was missing its final closing `}` for the class — confirmed
  via a top-level-brace grep (zero closing braces found for a class
  opened at line 89) before writing the one-line fix. This was the second
  distinct compile error in the same failed CI run, alongside the
  `MediaRepositoryImpl.kt` issue above.
- **`ui/navigation/PendingPlaybackHolder.kt`** — deleted (Dia deleted it
  directly in Spck Editor, confirmed gone on the next repo pull).

**Build verification, in detail:** first CI attempt after this session's
pastes failed for a reason initially suspected (and briefly assumed) to
be GitHub Actions cache-service infrastructure noise — the visible
annotations were all `"Our services aren't available right now"`
cache-restore warnings. This was **not verified further before Dia
retried the workflow**, which surfaced the actual compiler output (the
two real errors above). This is a process note worth remembering: an
infra-looking annotation set is not sufficient on its own to rule out a
real compile error underneath it — the actual task-level log output is
needed, and when the Actions API is rate-limited, a workflow retry (not
just re-reading the same failed run) may be the fastest way to get it.
Both real errors were fixed, re-pushed, and the retried run
(`runs/32750760290/job/97506903198`) came back green. Post-fix, all 12
files touched this session were re-pulled fresh and brace-balance
checked; `PendingPlaybackHolder.kt`'s absence and `PlayerNavArgs.kt`'s
presence were confirmed directly.

## Known, Deliberate Limitations (documented in code, not silently
worked around)

- **SearchScreen and Details' play actions always use Smart Defaults**
  (`preferredSource = null`, no stream-candidate picker) — unchanged,
  now also explicitly true of Home's and Player's own request-building,
  for the same reason (no picker UI exists yet).
- **Home and Details' play actions always start from position 0**
  (`resumeMs = null`) unless the request came from an actual
  `WatchedItem` (Continue Watching) — unchanged from Session 26.
- **Home's Continue Watching failure surface moved from an inline row
  state to Player's error screen** (Session 27) — a deliberate, discussed
  tradeoff, not a regression in capability. Tapping a row now always
  navigates instantly; if resolution fails, the user sees Player's
  Resolving state briefly, then its error card with Retry, and must
  press back to return to Home rather than staying there the whole time.
- **HomeScreen rows do not proactively resolve/display real
  titles/artwork** — unchanged. Resolution only happens once Player is
  reached.
- **`getEpisodeById()` has no dedicated per-episode cache entry or
  provider call** (Session 27) — implemented via the existing
  full-list `getEpisodes()` plus a filter, meaning a single-episode
  Player launch fetches the same data Details would for the whole show.
  Documented as an internal detail, not a public contract change, in
  `MediaRepository.kt`'s doc comment.
- **SettingsScreen preference edits write to Room on every single
  toggle/dropdown/keystroke** — unchanged, low priority.
- **SettingsScreen blank-name and delete-active-profile validation
  errors both surface as a generic error message** — `AppError` has no
  `ValidationError` case yet. See Open TODOs.

## Carried-Forward Lessons

- **An infra-looking CI annotation set (e.g. Gradle cache-service
  warnings) does not rule out a real compile error underneath it** —
  confirmed the hard way this session. Get the actual task-level compiler
  output before concluding a failure is "just infrastructure noise,"
  especially if the only evidence checked so far is the Annotations
  summary rather than the full job log.
- **Before writing a fix for apparently-lost code, check whether it's
  actually lost** (git history, or a duplicate/earlier occurrence in the
  same file) rather than reconstructing it from memory — reconstructing
  from memory is exactly the kind of error that caused problems earlier
  in this same session (`str_replace` anchor landing in an unintended
  location). Confirmed this session: the "lost" `resolveStream()` body
  was fully intact elsewhere in the file.
- **A `str_replace` or full-file insertion needs the surrounding class
  structure re-verified, not just the immediate anchor text** — matching
  a signature line is not sufcient to guarantee the insertion lands
  inside the intended scope if the file's closing brace could plausibly
  be adjacent to that anchor.
- **A brace-balance grep (`grep -c "^}"` for top-level, or an open/close
  count) is a cheap, fast sanity check worth running on any file after a
  structural edit**, before pushing and relying on CI alone to catch it.
- **Removing a resolve-before-navigate pattern from a ViewModel usually
  also means removing UI-layer state for it** (Session 27,
  `HomeViewModel`/`HomeScreen`) — don't leave a spinner/error path
  rendering dead state after the producing logic is gone.
- **Simplifying a caller's responsibility (e.g. no longer needing to
  resolve `Media` before navigating) often removes a dependency
  entirely**, not just a code path — `HomeViewModel` no longer needs
  `GetMediaByIdUseCase`; `DetailsViewModel` no longer needs
  `GetActiveProfileUseCase`. Worth checking imports/constructor params
  for now-unused dependencies after this kind of simplification, not just
  the method bodies.
- **A one-shot ViewModel → UI event needs a `Channel`, not a second
  `StateFlow`** — `Channel<T>(Channel.BUFFERED)` + `receiveAsFlow()`,
  collected via `LaunchedEffect` + `collectLatest` in the composable.
- **Nav args need explicit `NavType` registration** — `composable(route,
  arguments = listOf(navArgument("x") { type = NavType.StringType }))`;
  `SavedStateHandle["x"]` in the ViewModel picks it up automatically via
  `hilt-navigation-compose`.
- **Nav Compose has no nullable-String/Long NavType that round-trips
  cleanly through the simple `navArgument {}` builder** — sentinel values
  (`"none"` for String, `-1L` for Long) are the workaround, documented at
  both the encoding site (`Route.build()`) and decoding site
  (`SavedStateHandle` reads in the ViewModel).
- **Jetpack Navigation Compose cannot pass domain objects as nav args** —
  primitives only. As of Session 27, this is no longer worked around via
  an in-memory singleton (`PendingPlaybackHolder`, deleted this session)
  for the Player route — all callers now resolve from primitive nav args
  instead.
- **When a shared-IP GitHub Actions API rate limit blocks CI
  verification, ask for the direct run/job URL and `web_fetch` it
  instead** — confirmed again this session.
- **Grep `build.gradle.kts` / `libs.versions.toml` before importing
  anything from a library not yet used elsewhere in the codebase.**
- **Don't declare a session or a file "done" without an actual CI
  result** — confirmed hard this session: the first "looks probably
  fine" read of the failure would have been wrong.
- **`@Composable` functions are only callable from other `@Composable`
  functions** — not from `LaunchedEffect`, coroutine scopes, or other
  suspend contexts.
- **Flow collection:** always `flow.onEach{}.launchIn(viewModelScope)`,
  never `viewModelScope.launch { flow.collect() }`.
- **`MutableStateFlow.update{}` does not resolve in this build
  environment** — use direct `_flow.value = _flow.value.copy(...)`
  assignment instead.
- **`PlaybackState` naming collision:** `CoordinatorState` (sealed
  interface, `PlaybackCoordinator.kt`) vs `PlayerLifecycleState` (enum,
  `SessionState.kt`) — resolved via import aliases, reused again this
  session in `PlayerViewModel.kt`/`PlayerScreen.kt`.
- **ExoPlayer instance belongs in the Compose screen, not the
  ViewModel** (`PlayerScreen.kt`'s `DisposableEffect(Unit).onDispose`
  pattern) — unchanged, confirmed still correct after this session's
  `PlayerScreen.kt` rewrite.
- **When a paste truncates at a consistent point across retries, that's
  a signal to chunk the paste, not retry it unchanged.**
- **currentsprint.md on GitHub is the authoritative completion record**
  — project file copies / prior-session memory summaries are a
  convenience cache only and can be stale.

## Next Steps, In Order

1. **Stream-candidate picker UI.** Not started. Needed to lift the
   Smart-Defaults-only limitation from Home, Search/Details, and Player's
   own request-building (`preferredSource` is `null` everywhere today).
2. **Continue Watching → Details routing with resumePositionMs.** Not
   started. Would mean deciding whether Continue Watching should route
   through Details after all now that Player resolves from nav args
   cleanly either way — worth revisiting now that the nav-arg pattern is
   proven end-to-end for Player specifically, not just Details.
3. **Comment-only `PendingPlaybackHolder` cleanup** in
   `SearchViewModel.kt`, `SearchScreen.kt`, `MediaCache.kt` — punted from
   Session 27 due to time. No functional impact (stale prose only), but
   should be cleared soon so nothing points at a deleted file.
4. **`AppError.ValidationError` case.** Would let profile-related
   validation errors, and now also `getEpisodeById()`'s not-found case,
   surface distinct user-facing messages instead of reusing
   `LocalStorageError`/`Unknown`. Low urgency, growing slightly with each
   session that reuses `Unknown` as a catch-all.
5. **Completion-percentage / markAsCompleted wiring.** Not started.
6. **SettingsScreen preference-write debounce** — only if it turns out
   to feel laggy on-device; not confirmed, just flagged.
7. **HomeScreen proactive title/artwork display** — only if a priority.

## Open TODOs (carried forward, unchanged unless noted)

- App icon: placeholder system drawable in AndroidManifest.xml
- SearchRepository.updateSearchSession uses `Map<String, String>` for
  filters; revisit if SearchFilters gets promoted to a domain model
- AppError has no ValidationError case; CreateProfileUseCase and
  UpdateProfileUseCase use LocalStorageError(IllegalArgumentException)
  for blank name validation. See Next Steps #4 — now also relevant to
  getEpisodeById()'s AppError.Unknown not-found case (Session 27).
- StartPlaybackUseCase uses a fully qualified AppError reference inline;
  tidy to a top-level import if preferred
- HomeViewModel.removeItem() has no failure feedback path — deliberately
  deferred
- SearchScreen.kt uses fully-qualified
  `androidx.compose.foundation.text.KeyboardActions`/`KeyboardOptions`
  inline rather than top-level imports. Revisit once confirmed safe.
- `DropdownField<T>` in SettingsScreen.kt is a `TextButton` +
  `DropdownMenu`, not Material 3's `ExposedDropdownMenuBox`. Functional;
  revisit only if visual polish becomes a priority.
- No language list/picker exists — free-text BCP-47 code entry fields
  only.
- `PlaybackRepositoryImpl`'s `markAsCompleted()` block has inconsistent
  indentation — cosmetic only.
- `ExoPlayer.duration` can report `C.TIME_UNSET` before a stream has
  buffered enough to know its length; stored as-is currently.
- `MediaCache`'s 7-day TTL is a starting assumption, not derived from a
  specific requirement.
- **NEW (Session 27):** `SearchViewModel.kt`, `SearchScreen.kt`,
  `MediaCache.kt` all still have doc-comment-only references to
  `PendingPlaybackHolder`, which was deleted this session. No functional
  impact; punted to next session for cleanup (see Next Steps #3).
- **NEW (Session 27):** `R.string.home_resolving_media` and
  `R.string.home_resume_error` are now unused (the UI states that read
  them were removed when Home stopped resolving Media before
  navigating). Left in place, same handling as `search_tv_show_unsupported`.
- **NEW (Session 27):** `getEpisodeById()`'s not-found path reuses
  `AppError.Unknown` — not semantically ideal (see Next Steps #4), but
  consistent with `Unknown` already being this codebase's catch-all.
- **CARRIED (Session 26):** `SearchUiState.activeProfileId` is dead
  state. Left in place per Dia's explicit call; revisit near project end
  if still un